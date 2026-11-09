package com.amandadamata.ticketing.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HoldRequest(
        @NotBlank @Email @Size(max = 254) String customerEmail,
        @NotNull @Min(1) @Max(10) Integer quantity) {
}
