ALTER TABLE notification_history
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(64);

ALTER TABLE notification_history
    ADD COLUMN IF NOT EXISTS provider VARCHAR(64);

ALTER TABLE notification_history
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_notification_history_tenant_event_created_at
    ON notification_history (tenant_id, event_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_preferences_tenant_user_channel
    ON notification_preferences (tenant_id, user_id, channel);

CREATE INDEX IF NOT EXISTS idx_notification_preferences_tenant_user
    ON notification_preferences (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS processed_notifications (
    event_id VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_processed_notifications_processed_at
    ON processed_notifications (processed_at);

