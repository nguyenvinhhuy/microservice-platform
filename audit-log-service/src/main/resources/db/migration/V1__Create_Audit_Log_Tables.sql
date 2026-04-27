CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGSERIAL       NOT NULL,
    event_id    VARCHAR(64)     NOT NULL,
    event_type  VARCHAR(100)    NOT NULL,
    source      VARCHAR(100),
    tenant_id   BIGINT,
    user_id     BIGINT,
    aggregate_id    VARCHAR(100),
    aggregate_type  VARCHAR(100),
    correlation_id  VARCHAR(100),
    causation_id    VARCHAR(100),
    raw_payload TEXT            NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_log_event_id
    ON audit_log (event_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_id
    ON audit_log (tenant_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_user
    ON audit_log (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_type
    ON audit_log (tenant_id, event_type);

CREATE INDEX IF NOT EXISTS idx_audit_log_received_at
    ON audit_log (received_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id         VARCHAR(64)  NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

