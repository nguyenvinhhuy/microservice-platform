ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

ALTER TABLE payment_outbox
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS span_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payment_outbox_status_created ON payment_outbox (status, created_at);
CREATE INDEX IF NOT EXISTS idx_payments_processing_updated ON payments (status, updated_at);
