CREATE TABLE notification_history (
    id UUID PRIMARY KEY,
    user_id BIGINT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(64),
    type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    priority VARCHAR(16),
    provider VARCHAR(64),
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_history_tenant_user_created_at
    ON notification_history (tenant_id, user_id, created_at DESC);

CREATE INDEX idx_notification_history_tenant_event_created_at
    ON notification_history (tenant_id, event_id, created_at DESC);

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_notification_preferences_tenant_user_channel
    ON notification_preferences (tenant_id, user_id, channel);

CREATE INDEX idx_notification_preferences_tenant_user
    ON notification_preferences (tenant_id, user_id);

CREATE TABLE processed_notifications (
    event_id VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, channel)
);

CREATE INDEX idx_processed_notifications_processed_at
    ON processed_notifications (processed_at);

CREATE TABLE processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at);

CREATE TABLE kafka_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(200) NULL,
    payload TEXT NOT NULL,
    headers_json TEXT NULL,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_kafka_outbox_status_due_at
    ON kafka_outbox (status, due_at);

CREATE INDEX idx_kafka_outbox_created_at
    ON kafka_outbox (created_at DESC);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE processed_events_v2 (
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, event_id, consumer_service)
);

CREATE INDEX idx_processed_events_v2_processed_at
    ON processed_events_v2 (processed_at);

CREATE TABLE processed_notifications_v2 (
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, event_id, channel)
);

CREATE INDEX idx_processed_notifications_v2_processed_at
    ON processed_notifications_v2 (processed_at);
