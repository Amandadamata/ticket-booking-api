package com.amandadamata.ticketing.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Base class for tests against a real PostgreSQL 18 server.
 *
 * <p>The server runs from embedded binaries, so the tests need neither Docker nor a local
 * install: the same suite runs on a laptop and in CI. It starts once per test run, Flyway
 * applies the real migrations, and every test starts with empty tables.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTest.TestClockConfig.class)
public abstract class PostgresTest {

    public static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");

    private static final EmbeddedPostgres POSTGRES = start();

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @BeforeEach
    void cleanDatabaseAndResetClock() {
        jdbc.execute("TRUNCATE reservations, events RESTART IDENTITY");
        clock.set(NOW);
    }

    protected int availableSeats(long eventId) {
        return jdbc.queryForObject("SELECT available_seats FROM events WHERE id = ?", Integer.class, eventId);
    }

    protected int reservationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class);
    }

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the embedded PostgreSQL", e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        /** Replaces the system clock everywhere a {@code Clock} is injected. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }
}
