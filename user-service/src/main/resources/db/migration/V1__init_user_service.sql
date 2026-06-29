CREATE TABLE users (
    id UUID PRIMARY KEY,
    keycloak_user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    phone_number VARCHAR(64),
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    locale VARCHAR(32),
    timezone VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_tenant_keycloak_user_id UNIQUE (tenant_id, keycloak_user_id)
);

CREATE INDEX idx_users_tenant_keycloak_user_id ON users (tenant_id, keycloak_user_id);
CREATE INDEX idx_users_tenant_email ON users (tenant_id, email);
CREATE INDEX idx_users_tenant_status ON users (tenant_id, status);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    email_enabled BOOLEAN NOT NULL,
    sms_enabled BOOLEAN NOT NULL,
    push_enabled BOOLEAN NOT NULL,
    marketing_enabled BOOLEAN NOT NULL,
    language VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_preferences_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_preferences_tenant_user ON user_preferences (tenant_id, user_id);

CREATE TABLE user_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    label VARCHAR(80),
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    address_line VARCHAR(255) NOT NULL,
    postal_code VARCHAR(30),
    is_default BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_addresses_tenant_user ON user_addresses (tenant_id, user_id);
CREATE INDEX idx_user_addresses_tenant_user_default ON user_addresses (tenant_id, user_id, is_default);

CREATE TABLE user_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_memberships_tenant_user_role UNIQUE (tenant_id, user_id, role),
    CONSTRAINT fk_user_memberships_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_memberships_tenant_user ON user_memberships (tenant_id, user_id);
CREATE INDEX idx_user_memberships_tenant_role ON user_memberships (tenant_id, role, status);

CREATE TABLE kafka_outbox (
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

CREATE INDEX idx_kafka_outbox_status_due_at ON kafka_outbox (status, due_at);
CREATE INDEX idx_kafka_outbox_purpose_status ON kafka_outbox (purpose, status);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

