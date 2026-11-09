package com.amandadamata.ticketing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the expiry job. Tests switch it off and run the job by hand with a fixed clock. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "booking.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
