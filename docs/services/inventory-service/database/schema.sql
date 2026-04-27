-- =============================================================================
-- inventory-service - Consolidated PostgreSQL schema
-- Source: src/main/resources/db/migration/V1..V3
-- =============================================================================

CREATE TABLE IF NOT EXISTS inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    total_stock INTEGER NOT NULL,
    reserved_stock INTEGER NOT NULL,
    tenant_id BIGINT NOT NULL,
    version BIGINT,
    CONSTRAINT uk_inventory_tenant_product UNIQUE (tenant_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_tenant_product
    ON inventory (tenant_id, product_id);

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL,
    order_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    amount NUMERIC(19,2),
    currency VARCHAR(3),
    payment_provider VARCHAR(64),
    idempotency_key VARCHAR(128),
    correlation_id VARCHAR(128),
    trace_id VARCHAR(64),
    CONSTRAINT uk_reservation_tenant_reservation_id UNIQUE (tenant_id, reservation_id),
    CONSTRAINT uk_reservation_tenant_order_id UNIQUE (tenant_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_reservation_tenant_order
    ON inventory_reservation (tenant_id, order_id);

CREATE INDEX IF NOT EXISTS idx_reservation_status_expires
    ON inventory_reservation (status, expires_at);

CREATE TABLE IF NOT EXISTS inventory_reservation_item (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_reservation_item_reservation
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reservation_item_reservation
    ON inventory_reservation_item (reservation_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    partition_key VARCHAR(80),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(128),
    trace_id VARCHAR(64),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_started_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inventory_outbox_status_next
    ON outbox_events (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_inventory_outbox_event_id
    ON outbox_events (event_id);

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
