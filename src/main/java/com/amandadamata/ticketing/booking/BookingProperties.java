package com.amandadamata.ticketing.booking;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param holdDuration how long a reservation keeps its seats before it must be confirmed
 */
@ConfigurationProperties("booking")
public record BookingProperties(Duration holdDuration) {

    public BookingProperties {
        if (holdDuration == null || holdDuration.isNegative() || holdDuration.isZero()) {
            throw new IllegalArgumentException("booking.hold-duration must be positive");
        }
    }
}
