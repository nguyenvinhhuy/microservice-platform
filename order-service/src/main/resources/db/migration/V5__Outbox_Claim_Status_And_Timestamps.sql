ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_processing_started
    ON outbox_events (processing_started_at);

