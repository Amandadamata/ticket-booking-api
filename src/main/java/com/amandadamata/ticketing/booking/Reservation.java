package com.amandadamata.ticketing.booking;

import java.time.Instant;
import java.util.UUID;

import com.amandadamata.ticketing.event.Event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Seats held for one customer. The state rules live here, in plain methods that are easy to
 * test without a database; {@link ReservationService} decides when to call them and moves the
 * seats.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Column(name = "event_id", insertable = false, updatable = false)
    private Long eventId;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    protected Reservation() {
        // for JPA
    }

    Reservation(Event event, String customerEmail, int quantity, Instant now, Instant expiresAt,
            String idempotencyKey, String requestFingerprint) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.eventId = event.getId();
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.status = ReservationStatus.HELD;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    /** A hold that has reached its deadline, even if the expiry job hasn't run yet. */
    public boolean holdHasRunOut(Instant now) {
        return status == ReservationStatus.HELD && !now.isBefore(expiresAt);
    }

    /** @throws BookingException if the reservation isn't a hold that is still valid */
    void confirm(Instant now) {
        if (status != ReservationStatus.HELD) {
            throw BookingException.invalidState(id, status, "confirm");
        }
        if (holdHasRunOut(now)) {
            throw BookingException.holdExpired(id, expiresAt);
        }
        moveTo(ReservationStatus.CONFIRMED, now);
    }

    /**
     * Cancels a hold or a confirmed reservation.
     *
     * @return true if the seats must be given back; false if the reservation was already
     *         cancelled or expired, because its seats are already back on sale (so calling
     *         cancel twice is safe and changes nothing)
     */
    boolean cancel(Instant now) {
        return switch (status) {
            case CANCELLED, EXPIRED -> false;
            case HELD, CONFIRMED -> {
                moveTo(ReservationStatus.CANCELLED, now);
                yield true;
            }
        };
    }

    /** Only the expiry job and a late confirmation call this, always on a hold that ran out. */
    void expire(Instant now) {
        if (!holdHasRunOut(now)) {
            throw new IllegalStateException("Reservation " + id + " is not an expired hold");
        }
        moveTo(ReservationStatus.EXPIRED, now);
    }

    private void moveTo(ReservationStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    boolean matchesRequest(String fingerprint) {
        return requestFingerprint != null && requestFingerprint.equals(fingerprint);
    }

    public UUID getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
