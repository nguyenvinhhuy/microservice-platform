CREATE TABLE api_idempotency (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    state VARCHAR(20) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_api_idempotency_scope UNIQUE (tenant_id, user_id, operation, idempotency_key)
);

CREATE INDEX idx_api_idempotency_expires_at ON api_idempotency (expires_at);
CREATE INDEX idx_api_idempotency_state_expires ON api_idempotency (state, expires_at);

CREATE UNIQUE INDEX uk_user_addresses_default_per_user
    ON user_addresses (tenant_id, user_id)
    WHERE is_default;

