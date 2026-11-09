package com.amandadamata.ticketing.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.amandadamata.ticketing.event.Event;
import com.amandadamata.ticketing.event.EventRepository;

/**
 * The booking rules: hold seats, confirm, cancel and expire.
 *
 * <p>Each public method runs in its own short transaction. Transactions are opened with a
 * {@link TransactionTemplate} instead of {@code @Transactional} because {@link #hold} needs to
 * react to a failed transaction (a duplicate Idempotency-Key) by starting a new one.
 */
@Service
public class ReservationService {

    /** Expired holds handled per run of the expiry job, so one run never locks too many rows. */
    static final int EXPIRY_BATCH_SIZE = 100;

    private final EventRepository events;
    private final ReservationRepository reservations;
    private final BookingProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ReservationService(EventRepository events, ReservationRepository reservations,
            BookingProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.reservations = reservations;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Holds seats for {@link BookingProperties#holdDuration()}.
     *
     * <p>With an Idempotency-Key, sending the same request again returns the reservation that
     * was already created, so a client that lost the response can retry without buying twice.
     * If two copies arrive at the same moment, the unique index on the key lets only one insert
     * succeed; the other transaction rolls back (giving its seats back) and returns the winner.
     */
    public HoldResult hold(HoldCommand command, String idempotencyKey) {
        String fingerprint = idempotencyKey == null ? null : command.fingerprint();
        if (idempotencyKey != null) {
            Optional<HoldResult> earlier = transaction.execute(status -> replay(idempotencyKey, fingerprint));
            if (earlier.isPresent()) {
                return earlier.get();
            }
        }
        try {
            return transaction.execute(status -> createHold(command, idempotencyKey, fingerprint));
        } catch (DataIntegrityViolationException duplicateKey) {
            if (idempotencyKey == null) {
                throw duplicateKey;
            }
            return transaction.execute(status -> replay(idempotencyKey, fingerprint))
                    .orElseThrow(() -> duplicateKey);
        }
    }

    public ReservationDetails find(UUID id) {
        return transaction.execute(status -> ReservationDetails.of(reservations.findById(id)
                .orElseThrow(() -> BookingException.reservationNotFound(id))));
    }

    /**
     * Confirms a hold that is still valid.
     *
     * <p>If the time ran out but the expiry job hasn't run yet, the hold is expired here and its
     * seats are released before the error is returned, so the answer and the data agree.
     */
    public ReservationDetails confirm(UUID id) {
        Instant now = clock.instant();
        Optional<ReservationDetails> lateHold = transaction.execute(status -> {
            Reservation reservation = lockReservation(id);
            if (reservation.holdHasRunOut(now)) {
                expire(reservation, now);
                return Optional.of(ReservationDetails.of(reservation));
            }
            reservation.confirm(now);
            return Optional.<ReservationDetails>empty();
        });
        if (lateHold.isPresent()) {
            throw BookingException.holdExpired(id, lateHold.get().expiresAt());
        }
        return find(id);
    }

    /** Cancels a hold or a confirmed reservation and gives the seats back. Safe to repeat. */
    public ReservationDetails cancel(UUID id) {
        Instant now = clock.instant();
        return transaction.execute(status -> {
            Reservation reservation = lockReservation(id);
            if (reservation.holdHasRunOut(now)) {
                expire(reservation, now);
                return ReservationDetails.of(reservation);
            }
            if (reservation.cancel(now)) {
                events.releaseSeats(reservation.getEventId(), reservation.getQuantity());
            }
            return ReservationDetails.of(reservation);
        });
    }

    /**
     * Expires holds whose time ran out and gives their seats back.
     *
     * @return how many holds were expired in this run
     */
    public int expireRunOutHolds() {
        Instant now = clock.instant();
        Integer expired = transaction.execute(status -> {
            List<Reservation> runOut = reservations.findRunOutHolds(ReservationStatus.HELD, now,
                    Limit.of(EXPIRY_BATCH_SIZE));
            runOut.forEach(reservation -> expire(reservation, now));
            return runOut.size();
        });
        return expired == null ? 0 : expired;
    }

    private HoldResult createHold(HoldCommand command, String idempotencyKey, String fingerprint) {
        long eventId = command.eventId();
        Event event = events.findById(eventId).orElseThrow(() -> BookingException.eventNotFound(eventId));
        Instant now = clock.instant();
        if (event.salesClosed(now)) {
            throw BookingException.salesClosed(eventId);
        }
        if (events.takeSeats(eventId, command.quantity()) == 0) {
            int left = events.findById(eventId).map(Event::getAvailableSeats).orElse(0);
            throw BookingException.notEnoughSeats(eventId, command.quantity(), left);
        }
        Reservation reservation = new Reservation(events.getReferenceById(eventId),
                command.customerEmail(), command.quantity(), now, now.plus(properties.holdDuration()),
                idempotencyKey, fingerprint);
        // Flush now so a duplicate Idempotency-Key fails inside this transaction, which then
        // rolls back the seats taken above.
        reservations.saveAndFlush(reservation);
        return new HoldResult(ReservationDetails.of(reservation), false);
    }

    private Optional<HoldResult> replay(String idempotencyKey, String fingerprint) {
        return reservations.findByIdempotencyKey(idempotencyKey).map(earlier -> {
            if (!earlier.matchesRequest(fingerprint)) {
                throw BookingException.idempotencyKeyReused(idempotencyKey);
            }
            return new HoldResult(ReservationDetails.of(earlier), true);
        });
    }

    private Reservation lockReservation(UUID id) {
        return reservations.findByIdForUpdate(id).orElseThrow(() -> BookingException.reservationNotFound(id));
    }

    private void expire(Reservation reservation, Instant now) {
        reservation.expire(now);
        events.releaseSeats(reservation.getEventId(), reservation.getQuantity());
    }
}
