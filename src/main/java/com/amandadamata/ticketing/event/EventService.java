package com.amandadamata.ticketing.event;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amandadamata.ticketing.booking.BookingException;

@Service
public class EventService {

    private final EventRepository events;
    private final Clock clock;

    public EventService(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Event create(String name, String venue, Instant startsAt, int capacity) {
        Instant now = clock.instant();
        if (!startsAt.isAfter(now)) {
            throw BookingException.eventInThePast(startsAt);
        }
        return events.save(new Event(name, venue, startsAt, capacity, now));
    }

    @Transactional(readOnly = true)
    public Event find(long id) {
        return events.findById(id).orElseThrow(() -> BookingException.eventNotFound(id));
    }

    @Transactional(readOnly = true)
    public Page<Event> list(Pageable pageable) {
        return events.findAll(pageable);
    }
}
