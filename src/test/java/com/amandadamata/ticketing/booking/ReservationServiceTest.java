package com.amandadamata.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.amandadamata.ticketing.event.EventService;
import com.amandadamata.ticketing.support.PostgresTest;

/** Booking rules against a real PostgreSQL, including requests that arrive at the same time. */
class ReservationServiceTest extends PostgresTest {

    @Autowired
    private ReservationService reservations;

    @Autowired
    private EventService events;

    private long eventWithSeats(int capacity) {
        return events.create("Jazz Night", "Blue Room", NOW.plus(Duration.ofDays(7)), capacity).getId();
    }

    @Test
    void holdTakesSeatsAndExpiresAfterTheConfiguredTime() {
        long eventId = eventWithSeats(10);

        HoldResult result = reservations.hold(new HoldCommand(eventId, "ana@example.com", 3), null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.HELD);
        assertThat(result.reservation().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(availableSeats(eventId)).isEqualTo(7);
    }

    @Test
    void neverSellsMoreSeatsThanExist() throws Exception {
        long eventId = eventWithSeats(10);
        int buyers = 40;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger soldOut = new AtomicInteger();
        List<Future<HoldResult>> attempts = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            for (int i = 0; i < buyers; i++) {
                HoldCommand command = new HoldCommand(eventId, "buyer" + i + "@example.com", 1);
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        return reservations.hold(command, null);
                    } catch (BookingException e) {
                        assertThat(e.code()).isEqualTo("NOT_ENOUGH_SEATS");
                        soldOut.incrementAndGet();
                        return null;
                    }
                }));
            }
            start.countDown();
            int sold = 0;
            for (Future<HoldResult> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS) != null) {
                    sold++;
                }
            }

            assertThat(sold).isEqualTo(10);
            assertThat(soldOut).hasValue(30);
        }
        assertThat(availableSeats(eventId)).isZero();
        assertThat(reservationCount()).isEqualTo(10);
    }

    @Test
    void notEnoughSeatsSaysHowManyAreLeft() {
        long eventId = eventWithSeats(5);
        reservations.hold(new HoldCommand(eventId, "ana@example.com", 4), null);

        assertThatThrownBy(() -> reservations.hold(new HoldCommand(eventId, "bruno@example.com", 2), null))
                .isInstanceOfSatisfying(BookingException.class, e -> {
                    assertThat(e.code()).isEqualTo("NOT_ENOUGH_SEATS");
                    assertThat(e.details()).containsEntry("availableSeats", 1);
                });
        assertThat(availableSeats(eventId)).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameReservation() {
        long eventId = eventWithSeats(10);
        HoldCommand command = new HoldCommand(eventId, "ana@example.com", 2);

        HoldResult first = reservations.hold(command, "order-123");
        HoldResult retry = reservations.hold(command, "order-123");

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.reservation().id()).isEqualTo(first.reservation().id());
        assertThat(availableSeats(eventId)).isEqualTo(8);
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void retriesArrivingTogetherCreateOnlyOneReservation() throws Exception {
        long eventId = eventWithSeats(10);
        HoldCommand command = new HoldCommand(eventId, "ana@example.com", 2);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        List<Future<?>> retries = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                retries.add(pool.submit(() -> {
                    start.await();
                    ids.add(reservations.hold(command, "double-click").reservation().id());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> retry : retries) {
                retry.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(ids).hasSize(1);
        assertThat(reservationCount()).isEqualTo(1);
        assertThat(availableSeats(eventId)).isEqualTo(8);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        long eventId = eventWithSeats(10);
        reservations.hold(new HoldCommand(eventId, "ana@example.com", 2), "order-123");

        assertThatThrownBy(() -> reservations.hold(new HoldCommand(eventId, "ana@example.com", 5), "order-123"))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThat(availableSeats(eventId)).isEqualTo(8);
    }

    @Test
    void salesCloseWhenTheEventStarts() {
        long eventId = eventWithSeats(10);
        clock.advance(Duration.ofDays(7));

        assertThatThrownBy(() -> reservations.hold(new HoldCommand(eventId, "ana@example.com", 1), null))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.code()).isEqualTo("SALES_CLOSED"));
        assertThat(availableSeats(eventId)).isEqualTo(10);
    }

    @Test
    void expiryJobReleasesSeatsOfHoldsThatWereNotConfirmed() {
        long eventId = eventWithSeats(10);
        UUID late = reservations.hold(new HoldCommand(eventId, "ana@example.com", 3), null).reservation().id();
        UUID paid = reservations.hold(new HoldCommand(eventId, "bruno@example.com", 2), null).reservation().id();
        reservations.confirm(paid);

        clock.advance(Duration.ofMinutes(9));
        assertThat(reservations.expireRunOutHolds()).isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(reservations.expireRunOutHolds()).isEqualTo(1);
        assertThat(reservations.expireRunOutHolds()).isZero();

        assertThat(reservations.find(late).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservations.find(paid).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(availableSeats(eventId)).isEqualTo(8);
    }

    @Test
    void lateConfirmationExpiresTheHoldAndReleasesSeats() {
        long eventId = eventWithSeats(10);
        UUID id = reservations.hold(new HoldCommand(eventId, "ana@example.com", 3), null).reservation().id();
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> reservations.confirm(id))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.code()).isEqualTo("HOLD_EXPIRED"));

        assertThat(reservations.find(id).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(availableSeats(eventId)).isEqualTo(10);
        assertThat(reservations.expireRunOutHolds()).isZero();
    }

    @Test
    void cancelReleasesSeatsOnceEvenIfRepeated() {
        long eventId = eventWithSeats(10);
        UUID id = reservations.hold(new HoldCommand(eventId, "ana@example.com", 4), null).reservation().id();
        reservations.confirm(id);

        reservations.cancel(id);
        reservations.cancel(id);

        assertThat(reservations.find(id).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(availableSeats(eventId)).isEqualTo(10);
    }

    @Test
    void confirmAndExpiryRacingLeaveConsistentSeats() throws Exception {
        long eventId = eventWithSeats(10);
        UUID id = reservations.hold(new HoldCommand(eventId, "ana@example.com", 3), null).reservation().id();
        clock.advance(Duration.ofMinutes(10));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> confirm = pool.submit(() -> {
                start.await();
                try {
                    reservations.confirm(id);
                } catch (BookingException expected) {
                    // the hold ran out; either thread may be the one that expires it
                }
                return null;
            });
            Future<Integer> job = pool.submit(() -> {
                start.await();
                return reservations.expireRunOutHolds();
            });
            start.countDown();
            confirm.get(30, TimeUnit.SECONDS);
            job.get(30, TimeUnit.SECONDS);
        }

        assertThat(reservations.find(id).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(availableSeats(eventId)).isEqualTo(10);
    }

    @Test
    void databaseRefusesToGiveBackMoreSeatsThanTheCapacity() {
        long eventId = eventWithSeats(10);

        assertThatThrownBy(() -> jdbc.update("UPDATE events SET available_seats = 11 WHERE id = ?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE events SET available_seats = -1 WHERE id = ?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
