CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    tenant_id BIGINT,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128),
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_payments_idempotency_key
    ON payments (idempotency_key);

CREATE INDEX idx_payments_processing_updated
    ON payments (status, updated_at);

CREATE TABLE payment_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    trace_id VARCHAR(64),
    span_id VARCHAR(32),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    processing_started_at TIMESTAMPTZ,
    publish_attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT
);

CREATE INDEX idx_payment_outbox_unpublished
    ON payment_outbox (published, created_at);

CREATE INDEX idx_payment_outbox_next_attempt
    ON payment_outbox (published, next_attempt_at);

CREATE INDEX idx_payment_outbox_status_created
    ON payment_outbox (status, created_at);

CREATE TABLE processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at);

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
