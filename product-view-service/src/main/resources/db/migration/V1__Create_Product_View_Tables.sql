CREATE TABLE IF NOT EXISTS product_view (
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(300),
    price NUMERIC(19, 2),
    stock INTEGER,
    status VARCHAR(40),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_product_view PRIMARY KEY (tenant_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_product_view_tenant
    ON product_view (tenant_id);

CREATE INDEX IF NOT EXISTS idx_product_view_updated_at
    ON product_view (updated_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

