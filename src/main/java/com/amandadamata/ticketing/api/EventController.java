package com.amandadamata.ticketing.api;

import java.net.URI;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amandadamata.ticketing.event.Event;
import com.amandadamata.ticketing.event.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events")
class EventController {

    private final EventService events;

    EventController(EventService events) {
        this.events = events;
    }

    @PostMapping
    @Operation(summary = "Create an event with a number of seats")
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        Event event = events.create(request.name(), request.venue(), request.startsAt(), request.capacity());
        return ResponseEntity.created(URI.create("/api/events/" + event.getId())).body(EventResponse.of(event));
    }

    @GetMapping
    @Operation(summary = "List events, soonest first")
    PageResponse<EventResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("startsAt").and(Sort.by("id")));
        return PageResponse.of(events.list(pageable), EventResponse::of);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an event and its available seats")
    EventResponse get(@PathVariable long id) {
        return EventResponse.of(events.find(id));
    }
}
