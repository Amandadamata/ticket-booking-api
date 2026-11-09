package com.amandadamata.ticketing.booking;

import java.time.Instant;
import java.util.UUID;

/** A read-only copy of a reservation, built inside the transaction that loaded it. */
public record ReservationDetails(UUID id, long eventId, String customerEmail, int quantity,
        ReservationStatus status, Instant createdAt, Instant expiresAt, Instant updatedAt) {

    static ReservationDetails of(Reservation reservation) {
        return new ReservationDetails(reservation.getId(), reservation.getEventId(),
                reservation.getCustomerEmail(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getCreatedAt(), reservation.getExpiresAt(), reservation.getUpdatedAt());
    }
}
