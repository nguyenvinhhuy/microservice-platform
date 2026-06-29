ALTER TABLE files
    ADD COLUMN IF NOT EXISTS object_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS retention_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS encryption_mode VARCHAR(40) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS encryption_key_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pending_delete_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_scan_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scan_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scan_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_scan_error VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_files_reconciliation_status_updated_at
    ON files (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_files_scan_retry_status_updated_at
    ON files (status, scan_retry_count, updated_at DESC);

CREATE TABLE IF NOT EXISTS checksum_blacklist (
    checksum_sha256 VARCHAR(64) PRIMARY KEY,
    tenant_id UUID,
    reason VARCHAR(500) NOT NULL,
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_checksum_blacklist_expires_at
    ON checksum_blacklist (expires_at);

CREATE TABLE IF NOT EXISTS download_tickets (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID,
    file_id UUID NOT NULL,
    single_use BOOLEAN NOT NULL,
    revoked BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_download_tickets_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_download_tickets_file_expires_at
    ON download_tickets (file_id, expires_at DESC);

CREATE INDEX IF NOT EXISTS idx_download_tickets_tenant_expires_at
    ON download_tickets (tenant_id, expires_at DESC);

