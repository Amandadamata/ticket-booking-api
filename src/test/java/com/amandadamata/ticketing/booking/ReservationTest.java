package com.amandadamata.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.amandadamata.ticketing.event.Event;

/** The state rules on their own, without Spring or a database. */
class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");
    private static final Duration HOLD = Duration.ofMinutes(10);

    private final Event event = new Event("Jazz Night", "Blue Room", NOW.plus(Duration.ofDays(7)), 100, NOW);

    private Reservation hold() {
        return new Reservation(event, "ana@example.com", 2, NOW, NOW.plus(HOLD), null, null);
    }

    @Test
    void newReservationIsAHoldUntilTheDeadline() {
        Reservation reservation = hold();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(reservation.holdHasRunOut(NOW.plus(HOLD).minusMillis(1))).isFalse();
        assertThat(reservation.holdHasRunOut(NOW.plus(HOLD))).isTrue();
    }

    @Test
    void holdCanBeConfirmedBeforeTheDeadline() {
        Reservation reservation = hold();

        reservation.confirm(NOW.plus(Duration.ofMinutes(9)));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(9)));
    }

    @Test
    void holdCannotBeConfirmedAtOrAfterTheDeadline() {
        Reservation reservation = hold();

        assertThatThrownBy(() -> reservation.confirm(NOW.plus(HOLD)))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.code()).isEqualTo("HOLD_EXPIRED"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
    }

    @Test
    void onlyAHoldCanBeConfirmed() {
        Reservation reservation = hold();
        reservation.confirm(NOW);

        assertThatThrownBy(() -> reservation.confirm(NOW))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_RESERVATION_STATE"));
    }

    @Test
    void cancellingReleasesSeatsOnlyTheFirstTime() {
        Reservation reservation = hold();

        assertThat(reservation.cancel(NOW)).isTrue();
        assertThat(reservation.cancel(NOW)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void confirmedReservationCanBeCancelled() {
        Reservation reservation = hold();
        reservation.confirm(NOW);

        assertThat(reservation.cancel(NOW)).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void expiredReservationHasNothingLeftToRelease() {
        Reservation reservation = hold();
        reservation.expire(NOW.plus(HOLD));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.cancel(NOW.plus(HOLD))).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void holdCannotExpireBeforeItsDeadline() {
        Reservation reservation = hold();

        assertThatThrownBy(() -> reservation.expire(NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fingerprintDependsOnWhatIsAskedNotOnEmailCase() {
        HoldCommand original = new HoldCommand(1, "Ana@Example.com ", 2);

        assertThat(original.fingerprint()).isEqualTo(new HoldCommand(1, "ana@example.com", 2).fingerprint());
        assertThat(original.fingerprint()).isNotEqualTo(new HoldCommand(1, "ana@example.com", 3).fingerprint());
        assertThat(original.fingerprint()).isNotEqualTo(new HoldCommand(2, "ana@example.com", 2).fingerprint());
    }
}
