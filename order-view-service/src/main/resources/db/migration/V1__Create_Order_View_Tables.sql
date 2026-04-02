CREATE TABLE IF NOT EXISTS order_view (
    tenant_id BIGINT NOT NULL,
    order_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(40),
    payment_status VARCHAR(40),
    stock_status VARCHAR(40),
    total_price NUMERIC(19, 2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_view PRIMARY KEY (tenant_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_order_view_tenant
    ON order_view (tenant_id);

CREATE INDEX IF NOT EXISTS idx_order_view_user
    ON order_view (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_order_view_created_at
    ON order_view (created_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

