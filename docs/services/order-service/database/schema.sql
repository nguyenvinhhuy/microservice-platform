-- =============================================================================
-- order-service - Consolidated PostgreSQL schema
-- Source: src/main/resources/db/migration/V1..V6
-- =============================================================================

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT,
    reservation_reference VARCHAR(128),
    payment_attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500)
);

CREATE INDEX idx_orders_tenant ON orders (tenant_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

CREATE TABLE order_items (
    order_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price_at_purchase NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE order_payments (
    order_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    provider VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT,
    CONSTRAINT fk_order_payments_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    api_name VARCHAR(40) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    order_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    response_payload TEXT,
    CONSTRAINT uk_idempotency_tenant_request_api UNIQUE (tenant_id, request_id, api_name)
);

CREATE INDEX idx_idempotency_tenant ON idempotency_keys (tenant_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    idempotency_key VARCHAR(120),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_started_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_events (status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_outbox_processing_started
    ON outbox_events (processing_started_at);

CREATE TABLE IF NOT EXISTS order_sagas (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id UUID NOT NULL,
    state VARCHAR(30) NOT NULL,
    payment_provider VARCHAR(80),
    payment_id UUID,
    request_id VARCHAR(120),
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT,
    CONSTRAINT uk_order_saga_tenant_order UNIQUE (tenant_id, order_id),
    CONSTRAINT fk_order_saga_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_saga_state_updated
    ON order_sagas (state, updated_at);

CREATE INDEX IF NOT EXISTS idx_order_saga_tenant_order
    ON order_sagas (tenant_id, order_id);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

-- Notes:
-- 1. `orders.status` values used by the current code are
--    CREATED, RESERVED, PAYMENT_IN_PROGRESS, CONFIRMED, CANCELLED, FAILED, COMPENSATING.
-- 2. `order_payments.status` values used by the current code are
--    INITIATED, SUCCESS, FAILED.
-- 3. processed_events is provisioned for future consumer-side idempotency only.
