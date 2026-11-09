package com.amandadamata.ticketing.booking;

/**
 * <pre>
 * HELD ──confirm──▶ CONFIRMED ──cancel──▶ CANCELLED
 *  │ └────────────────cancel──────────────────▲
 *  └──time runs out──▶ EXPIRED
 * </pre>
 * Seats are taken when the hold is created and given back on CANCELLED or EXPIRED.
 */
public enum ReservationStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
