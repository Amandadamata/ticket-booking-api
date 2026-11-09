package com.amandadamata.ticketing.booking;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

/**
 * A request that breaks a business rule. Each one carries the HTTP status, a stable
 * {@code code} that clients can switch on, and a message written for a person.
 */
public class BookingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final Map<String, Object> details;

    private BookingException(HttpStatus status, String code, String title, String message,
            Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.title = title;
        this.details = Map.copyOf(details);
    }

    public static BookingException eventNotFound(long eventId) {
        return new BookingException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found",
                "There is no event with id " + eventId + ".", Map.of());
    }

    public static BookingException reservationNotFound(UUID id) {
        return new BookingException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                "Reservation not found", "There is no reservation with id " + id + ".", Map.of());
    }

    public static BookingException eventInThePast(Instant startsAt) {
        return new BookingException(HttpStatus.UNPROCESSABLE_CONTENT, "EVENT_IN_THE_PAST",
                "Event date has passed", "The event must start in the future, but startsAt is "
                        + startsAt + ".", Map.of());
    }

    public static BookingException notEnoughSeats(long eventId, int requested, int available) {
        return new BookingException(HttpStatus.CONFLICT, "NOT_ENOUGH_SEATS", "Not enough seats",
                "You asked for " + requested + " seat(s), but only " + available + " are left.",
                Map.of("eventId", eventId, "requested", requested, "availableSeats", available));
    }

    public static BookingException salesClosed(long eventId) {
        return new BookingException(HttpStatus.CONFLICT, "SALES_CLOSED", "Sales are closed",
                "This event has already started, so tickets can no longer be reserved.",
                Map.of("eventId", eventId));
    }

    public static BookingException holdExpired(UUID id, Instant expiredAt) {
        return new BookingException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Hold expired",
                "This reservation was held until " + expiredAt
                        + " and was not confirmed in time. The seats were released.",
                Map.of("reservationId", id, "expiredAt", expiredAt));
    }

    public static BookingException invalidState(UUID id, ReservationStatus status, String action) {
        return new BookingException(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE",
                "Action not allowed", "A " + status + " reservation can't " + action + ".",
                Map.of("reservationId", id, "status", status));
    }

    public static BookingException idempotencyKeyReused(String key) {
        return new BookingException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency key already used",
                "The Idempotency-Key '" + key + "' was already used for a different request.",
                Map.of());
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public Map<String, Object> details() {
        return details;
    }
}
