CREATE TABLE IF NOT EXISTS dlq_events (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    partition INTEGER NOT NULL,
    offset BIGINT NOT NULL,
    key VARCHAR(500),
    payload TEXT NOT NULL,
    headers_json TEXT,
    original_topic VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_dlq_events_topic_partition_offset UNIQUE (topic, partition, offset)
);

CREATE INDEX IF NOT EXISTS idx_dlq_events_status_created
    ON dlq_events (status, created_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(200) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

