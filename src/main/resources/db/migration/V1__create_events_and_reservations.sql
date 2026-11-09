-- Events sell general admission seats. available_seats is the source of truth for what can
-- still be sold, and the CHECK makes the database refuse any update that would oversell.
CREATE TABLE events (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    venue           VARCHAR(120) NOT NULL CHECK (length(trim(venue)) > 0),
    starts_at       TIMESTAMPTZ  NOT NULL,
    capacity        INTEGER      NOT NULL CHECK (capacity BETWEEN 1 AND 100000),
    available_seats INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT events_available_seats_in_range CHECK (available_seats BETWEEN 0 AND capacity)
);

-- A reservation holds seats for a limited time. The idempotency key is unique, so two
-- retries of the same request can never create two reservations.
CREATE TABLE reservations (
    id                  UUID         PRIMARY KEY,
    event_id            BIGINT       NOT NULL REFERENCES events (id),
    customer_email      VARCHAR(254) NOT NULL,
    quantity            INTEGER      NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    status              VARCHAR(16)  NOT NULL
                        CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    idempotency_key     VARCHAR(100) UNIQUE,
    request_fingerprint VARCHAR(64),
    CONSTRAINT reservations_key_has_fingerprint
        CHECK ((idempotency_key IS NULL) = (request_fingerprint IS NULL))
);

-- The expiry job only looks at holds that are still waiting.
CREATE INDEX reservations_held_by_expiry ON reservations (expires_at) WHERE status = 'HELD';
CREATE INDEX reservations_by_event ON reservations (event_id);
