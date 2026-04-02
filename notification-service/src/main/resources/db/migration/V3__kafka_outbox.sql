CREATE TABLE IF NOT EXISTS kafka_outbox (
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

CREATE INDEX IF NOT EXISTS idx_kafka_outbox_status_due_at
    ON kafka_outbox (status, due_at);

CREATE INDEX IF NOT EXISTS idx_kafka_outbox_created_at
    ON kafka_outbox (created_at DESC);
