package com.amandadamata.ticketing.api;

import java.time.Instant;

import com.amandadamata.ticketing.event.Event;

public record EventResponse(long id, String name, String venue, Instant startsAt, int capacity,
        int availableSeats) {

    static EventResponse of(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getStartsAt(),
                event.getCapacity(), event.getAvailableSeats());
    }
}
