package com.amandadamata.ticketing.api;

import java.time.Instant;
import java.util.UUID;

import com.amandadamata.ticketing.booking.ReservationDetails;
import com.amandadamata.ticketing.booking.ReservationStatus;

public record ReservationResponse(UUID id, long eventId, String customerEmail, int quantity,
        ReservationStatus status, Instant createdAt, Instant expiresAt, Instant updatedAt) {

    static ReservationResponse of(ReservationDetails details) {
        return new ReservationResponse(details.id(), details.eventId(), details.customerEmail(),
                details.quantity(), details.status(), details.createdAt(), details.expiresAt(),
                details.updatedAt());
    }
}
