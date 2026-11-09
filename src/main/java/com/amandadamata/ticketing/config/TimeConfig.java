package com.amandadamata.ticketing.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every rule that depends on time (sales closing, hold expiry) reads this clock instead of
 * calling {@code Instant.now()}, so tests can move time forward without sleeping.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
