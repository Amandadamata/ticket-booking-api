package com.amandadamata.ticketing.booking;

/**
 * @param replayed true when an Idempotency-Key matched an earlier request and the original
 *                 reservation is returned instead of a new one
 */
public record HoldResult(ReservationDetails reservation, boolean replayed) {
}
