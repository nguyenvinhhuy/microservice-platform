ALTER TABLE processed_events
    ALTER COLUMN event_id TYPE VARCHAR(26)
    USING event_id::text;

