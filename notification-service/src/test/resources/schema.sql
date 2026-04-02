CREATE TABLE IF NOT EXISTS notification_history (
    id UUID PRIMARY KEY,
    user_id BIGINT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(64) NULL,
    type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NULL,
    provider VARCHAR(64) NULL,
    payload CLOB NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_history_tenant_user_created_at
    ON notification_history (tenant_id, user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_notification_history_tenant_event_created_at
    ON notification_history (tenant_id, event_id, created_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_notifications (
    event_id VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, channel)
);

CREATE TABLE IF NOT EXISTS kafka_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(200) NULL,
    payload CLOB NOT NULL,
    headers_json CLOB NULL,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_kafka_outbox_status_due_at
    ON kafka_outbox (status, due_at);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
