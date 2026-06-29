CREATE TABLE IF NOT EXISTS multipart_upload_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    file_id UUID NOT NULL,
    category VARCHAR(80) NOT NULL,
    bucket VARCHAR(120) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    expected_size_bytes BIGINT NOT NULL,
    expected_checksum_sha256 VARCHAR(64) NOT NULL,
    visibility VARCHAR(40) NOT NULL,
    metadata_json VARCHAR(4000),
    upload_id VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_multipart_upload_session_file FOREIGN KEY (file_id) REFERENCES files (id),
    CONSTRAINT uq_multipart_upload_session_upload_id UNIQUE (upload_id),
    CONSTRAINT chk_multipart_upload_expected_size_positive CHECK (expected_size_bytes > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_multipart_upload_session_active_file
    ON multipart_upload_session (file_id)
    WHERE status = 'INITIATED';

CREATE INDEX IF NOT EXISTS idx_multipart_upload_session_status_expires_at
    ON multipart_upload_session (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_multipart_upload_session_tenant_status_updated_at
    ON multipart_upload_session (tenant_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS multipart_upload_part (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    part_number INTEGER NOT NULL,
    etag VARCHAR(255) NOT NULL,
    checksum_sha256 VARCHAR(64),
    size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_multipart_upload_part_session FOREIGN KEY (session_id) REFERENCES multipart_upload_session (id) ON DELETE CASCADE,
    CONSTRAINT uq_multipart_upload_part_number UNIQUE (session_id, part_number),
    CONSTRAINT chk_multipart_upload_part_number_positive CHECK (part_number > 0),
    CONSTRAINT chk_multipart_upload_part_size_positive CHECK (size_bytes IS NULL OR size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_multipart_upload_part_session_number
    ON multipart_upload_part (session_id, part_number);

