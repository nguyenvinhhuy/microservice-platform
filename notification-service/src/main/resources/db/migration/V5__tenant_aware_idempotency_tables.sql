CREATE TABLE IF NOT EXISTS processed_events_v2 (
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_v2_processed_at
    ON processed_events_v2 (processed_at);

CREATE TABLE IF NOT EXISTS processed_notifications_v2 (
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_processed_notifications_v2_processed_at
    ON processed_notifications_v2 (processed_at);

