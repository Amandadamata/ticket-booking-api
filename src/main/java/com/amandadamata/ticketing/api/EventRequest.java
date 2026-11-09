package com.amandadamata.ticketing.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String venue,
        @NotNull Instant startsAt,
        @NotNull @Min(1) @Max(100_000) Integer capacity) {
}
