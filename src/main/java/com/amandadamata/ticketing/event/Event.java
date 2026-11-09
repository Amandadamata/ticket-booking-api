package com.amandadamata.ticketing.event;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An event with general admission seats.
 *
 * <p>{@code availableSeats} is never changed through this object. Seats are taken and released
 * with conditional SQL updates in {@link EventRepository}, so two requests can't read the same
 * number and both decide there is room.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {
        // for JPA
    }

    public Event(String name, String venue, Instant startsAt, int capacity, Instant createdAt) {
        this.name = name.strip();
        this.venue = venue.strip();
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.availableSeats = capacity;
        this.createdAt = createdAt;
    }

    /** Ticket sales stop when the event starts. */
    public boolean salesClosed(Instant now) {
        return !now.isBefore(startsAt);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
