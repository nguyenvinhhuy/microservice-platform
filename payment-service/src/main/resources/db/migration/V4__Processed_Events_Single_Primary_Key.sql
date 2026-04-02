DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'processed_events'
    ) THEN
        CREATE TABLE IF NOT EXISTS processed_events_v2 (
            event_id VARCHAR(64) PRIMARY KEY,
            consumer_service VARCHAR(100) NOT NULL,
            processed_at TIMESTAMPTZ NOT NULL
        );

        INSERT INTO processed_events_v2 (event_id, consumer_service, processed_at)
        SELECT CAST(event_id AS VARCHAR(64)), consumer_service, processed_at
        FROM processed_events
        ON CONFLICT (event_id) DO NOTHING;

        DROP TABLE processed_events;
        ALTER TABLE processed_events_v2 RENAME TO processed_events;
    ELSE
        CREATE TABLE IF NOT EXISTS processed_events (
            event_id VARCHAR(64) PRIMARY KEY,
            consumer_service VARCHAR(100) NOT NULL,
            processed_at TIMESTAMPTZ NOT NULL
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

