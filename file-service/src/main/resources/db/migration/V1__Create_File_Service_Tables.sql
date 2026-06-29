CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    category VARCHAR(80) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    bucket VARCHAR(120) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    visibility VARCHAR(40) NOT NULL,
    malware_scan_status VARCHAR(40) NOT NULL,
    metadata_json VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_files_object_key UNIQUE (object_key),
    CONSTRAINT chk_files_size_bytes_non_negative CHECK (size_bytes >= 0)
);

CREATE INDEX IF NOT EXISTS idx_files_tenant_created_at
    ON files (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_files_tenant_status_created_at
    ON files (tenant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_files_tenant_deleted_at
    ON files (tenant_id, deleted_at);

CREATE INDEX IF NOT EXISTS idx_files_status_created_at
    ON files (status, created_at);

CREATE TABLE IF NOT EXISTS file_access_audit (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    file_id UUID NOT NULL,
    actor_user_id UUID,
    action VARCHAR(60) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_file_access_audit_tenant_created_at
    ON file_access_audit (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_access_audit_file_created_at
    ON file_access_audit (file_id, created_at DESC);

CREATE TABLE IF NOT EXISTS file_quota (
    tenant_id UUID PRIMARY KEY,
    used_bytes BIGINT NOT NULL,
    quota_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_file_quota_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_file_quota_quota_positive CHECK (quota_bytes > 0)
);

CREATE TABLE IF NOT EXISTS api_idempotency (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_path VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL,
    response_body VARCHAR(8000),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_api_idempotency_tenant_path_key UNIQUE (tenant_id, request_path, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_api_idempotency_expires_at
    ON api_idempotency (expires_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(100) NOT NULL,
    consumer_service VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_service)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events (processed_at);

CREATE TABLE IF NOT EXISTS kafka_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(200),
    payload TEXT NOT NULL,
    headers_json TEXT,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_kafka_outbox_status_due_at
    ON kafka_outbox (status, due_at);

CREATE INDEX IF NOT EXISTS idx_kafka_outbox_purpose_status_due_at
    ON kafka_outbox (purpose, status, due_at);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

