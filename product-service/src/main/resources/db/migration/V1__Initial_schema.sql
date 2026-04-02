CREATE TABLE t_products (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    short_description VARCHAR(500),
    description TEXT,
    brand VARCHAR(100),
    category_id BIGINT NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    thumbnail_url VARCHAR(255),
    rating_average DOUBLE PRECISION,
    rating_count INT,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE INDEX idx_product_code ON t_products(code);
CREATE INDEX idx_product_slug ON t_products(slug);
CREATE INDEX idx_product_tenant_id ON t_products(tenant_id);
CREATE INDEX idx_product_status ON t_products(status);
CREATE INDEX idx_product_tenant_status ON t_products(tenant_id, status);

CREATE TABLE t_product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    url VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE t_product_attributes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    name VARCHAR(100) NOT NULL,
    value VARCHAR(255) NOT NULL
);

CREATE TABLE t_product_prices (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES t_products(id),
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP
);

CREATE TABLE idempotency_keys (
    idempotency_key UUID PRIMARY KEY,
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
