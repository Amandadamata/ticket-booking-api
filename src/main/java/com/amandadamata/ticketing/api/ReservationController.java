package com.amandadamata.ticketing.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.amandadamata.ticketing.booking.HoldCommand;
import com.amandadamata.ticketing.booking.HoldResult;
import com.amandadamata.ticketing.booking.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@Tag(name = "Reservations")
class ReservationController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final ReservationService reservations;

    ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/api/events/{eventId}/reservations")
    @Operation(summary = "Hold seats for a few minutes",
            description = "Returns 201 for a new hold. With an Idempotency-Key, repeating the same "
                    + "request returns 200 and the original reservation instead of a second one.")
    ResponseEntity<ReservationResponse> hold(@PathVariable long eventId,
            @Valid @RequestBody HoldRequest request,
            @Parameter(description = "Any unique value per purchase attempt, such as a UUID")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}", message = "must be 1 to 100 letters, digits or . _ : -")
            String idempotencyKey) {
        HoldResult result = reservations.hold(
                new HoldCommand(eventId, request.customerEmail(), request.quantity()), idempotencyKey);
        ReservationResponse body = ReservationResponse.of(result.reservation());
        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).header(REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/reservations/" + body.id())).body(body);
    }

    @GetMapping("/api/reservations/{id}")
    @Operation(summary = "Get a reservation")
    ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.of(reservations.find(id));
    }

    @PostMapping("/api/reservations/{id}/confirm")
    @Operation(summary = "Confirm a hold before it expires")
    ReservationResponse confirm(@PathVariable UUID id) {
        return ReservationResponse.of(reservations.confirm(id));
    }

    @PostMapping("/api/reservations/{id}/cancel")
    @Operation(summary = "Cancel a reservation and release its seats (safe to repeat)")
    ReservationResponse cancel(@PathVariable UUID id) {
        return ReservationResponse.of(reservations.cancel(id));
    }
}
