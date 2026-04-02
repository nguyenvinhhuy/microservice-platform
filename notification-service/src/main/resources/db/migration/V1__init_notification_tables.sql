CREATE TABLE IF NOT EXISTS notification_history (
    id UUID PRIMARY KEY,
    user_id BIGINT NULL,
    tenant_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_history_tenant_user_created_at
    ON notification_history (tenant_id, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

