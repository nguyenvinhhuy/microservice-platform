ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS reservation_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS payment_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);

ALTER TABLE idempotency_keys
    RENAME COLUMN action TO api_name;

ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    ADD COLUMN IF NOT EXISTS response_payload TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE idempotency_keys
    ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE idempotency_keys
    DROP CONSTRAINT IF EXISTS uk_idempotency_tenant_action_request;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT uk_idempotency_tenant_request_api UNIQUE (tenant_id, request_id, api_name);
