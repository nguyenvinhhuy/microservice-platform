CREATE TABLE IF NOT EXISTS t_products (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    short_description VARCHAR(500),
    description TEXT,
    brand VARCHAR(100),
    category_id BIGINT NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    thumbnail_url VARCHAR(255),
    rating_average DOUBLE PRECISION,
    rating_count INT,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_product_code ON t_products(code);
CREATE INDEX IF NOT EXISTS idx_product_slug ON t_products(slug);
CREATE INDEX IF NOT EXISTS idx_product_tenant_id ON t_products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_status ON t_products(status);
CREATE INDEX IF NOT EXISTS idx_product_tenant_status ON t_products(tenant_id, status);

CREATE TABLE IF NOT EXISTS t_product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_product_attributes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    name VARCHAR(100) NOT NULL,
    value VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS t_product_prices (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key UUID PRIMARY KEY,
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(120),
    idempotency_key VARCHAR(120),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_started_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_product_outbox_status_created
    ON outbox_events (status, created_at);

CREATE INDEX IF NOT EXISTS idx_product_outbox_status_next
    ON outbox_events (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_product_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

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
