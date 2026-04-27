# product-service

## 1. Purpose

`product-service` is the command and query owner for the product catalog. It stores tenant-scoped product data, exposes REST endpoints for catalog management and lookup, and publishes product update events through a transactional outbox.

## 2. Key Functions

| Function | Current implementation contract |
|---|---|
| Create product | `POST /api/v1/products` creates a new product and always persists it with `status = DRAFT`, even if another status is provided in the request body. |
| Update product | `PUT /api/v1/products/{id}` updates the aggregate, always emits `product.updated`, and conditionally emits `product.price.updated` when `price` or `currency` changes. |
| Soft delete | `DELETE /api/v1/products/{id}` marks the aggregate as `DELETED`. The row remains in the database. |
| Tenant-scoped reads | Public queries load products by `tenantId` and exclude `DELETED` rows unless the query explicitly targets another status. |
| Internal lookup | `GET /internal/products/{id}` returns a product snapshot for trusted service-to-service calls such as `order-service`. |
| Plan quota | `PlanService` returns a quota of `100` by default and `10000` when `UserContext.roles` contains `ROLE_PRO`. |
| Producer outbox | Product integration events are written to `outbox_events` and published by `ProductOutboxPublisher` with at-least-once delivery. |
| Producer-side idempotency | `POST /api/v1/products` uses optional `Idempotency-Key` as the canonical create deduplication header. `X-Request-Id` remains the tracing header and is accepted only as a temporary backward-compatible fallback. Duplicate requests replay only the cached HTTP status. |

## 3. API Surface

| Endpoint | Method | Role contract | Notes |
|---|---|---|---|
| `/api/v1/products` | `POST` | `ROLE_ADMIN` | Optional `Idempotency-Key` is the canonical create header; `X-Request-Id` is accepted only as a temporary compatibility fallback. Response body is empty. |
| `/api/v1/products` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Paginated tenant-scoped list excluding `DELETED`. |
| `/api/v1/products/{id}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Tenant-scoped lookup by numeric id. |
| `/api/v1/products/code/{code}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Tenant-scoped lookup by code. |
| `/api/v1/products/slug/{slug}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Tenant-scoped lookup by slug. |
| `/api/v1/products/{id}` | `PUT` | `ROLE_ADMIN` | Full update; response body is empty. |
| `/api/v1/products/{id}` | `DELETE` | `ROLE_ADMIN` | Soft delete; response body is empty. |
| `/api/v1/products/search` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Searches by `name`, `code`, or `brand`. |
| `/api/v1/products/status/{status}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Paginated status filter. |
| `/api/v1/products/status-list/{status}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | List form of the same status filter. |
| `/api/v1/products/category/{categoryId}` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Paginated category filter. |
| `/api/v1/products/price-range` | `GET` | `ROLE_ADMIN`, `ROLE_VIEWER` | Filters by `status`, `minPrice`, and `maxPrice`. |
| `/internal/products/{id}` | `GET` | Trusted internal caller | No method-level RBAC; rely on network and gateway trust boundary. |

## 4. Domain Rules

| Rule | Current implementation contract |
|---|---|
| Tenant ownership | All repository lookups used by service methods include `tenantId`. Cross-tenant reads and writes are blocked by query shape. |
| Create status | `createProduct()` ignores the incoming status and persists `DRAFT`. |
| Request DTO shape | `ProductDTO` currently requires both `tenantId` and `status` in the request body due to bean validation, even though create overrides both values internally. |
| Quota enforcement | Product creation fails with `QuotaExceededException` when the tenant already owns `quota` non-`DELETED` products. |
| Code and slug checks | Service-layer checks are tenant-scoped, but the database schema also enforces global uniqueness on `code` and `slug`. |
| Soft delete | Deleted rows remain in `t_products`; normal public queries exclude them using `status <> DELETED`. |
| Price history bootstrap | If `priceHistory` is omitted during create, one entry is generated from the root `price` and `currency`. |
| Price update event | `product.price.updated` is emitted only when the stored `price` or `currency` changes. |

## 5. Event Flows

### Outbound events

| Event type | Topic | Trigger | Payload class |
|---|---|---|---|
| `product.updated` | `product.events` | Every successful `PUT /api/v1/products/{id}` | `ProductUpdatedEvent` |
| `product.price.updated` | `product.events` | Successful update where `price` or `currency` changed | `ProductPriceUpdatedEvent` |

### Publication model

- Events are persisted to `outbox_events` in the same transaction as the product mutation.
- `ProductOutboxPublisher` claims `PENDING` and `FAILED` rows with `FOR UPDATE SKIP LOCKED`.
- Kafka publish uses key = `aggregateId`, which is currently `product-{productId}`.
- Envelope `eventType` values are `product.updated` and `product.price.updated`.
- Envelope `dataSchema` values are `product.updated.v1` and `product.price.updated.v1`.
- Failed publishes use exponential backoff: `min(60, 2^min(retryCount, 6))` seconds.
- No Kafka consumer exists in this service. `processed_events` is provisioned only for future use.

## 6. Persistence and Background Jobs

| Table | Purpose |
|---|---|
| `t_products` | Main product aggregate root. |
| `t_product_images` | Child collection for product images. |
| `t_product_attributes` | Child collection for product attributes. |
| `t_product_prices` | Child collection for price history. |
| `idempotency_keys` | Producer-side create request deduplication. |
| `outbox_events` | Transactional outbox for Kafka publication. |
| `shedlock` | Distributed scheduler lock table. |
| `processed_events` | Reserved for future consumer-side idempotency. |

Scheduled jobs:

- `ProductOutboxPublisher` runs on `${product.outbox.publisher-delay-ms:2000}` with ShedLock name `product-service-outbox-publisher`.
- `OutboxMonitoringMetrics` refreshes `outbox_oldest_event_age` every `${product.outbox.metrics.interval-ms:15000}`.

## 7. Error and Idempotency Semantics

### Idempotency

- Only `POST /api/v1/products` participates in idempotency.
- `Idempotency-Key` is the canonical optional UUID header for create deduplication.
- `X-Request-Id` remains the tracing header and is accepted only as a temporary backward-compatible fallback on create.
- New callers should send `Idempotency-Key` and should not rely on `X-Request-Id` for business deduplication.
- When a key already exists, the controller returns the cached HTTP status and no response body.
- There is no `PROCESSING/COMPLETED/FAILED` lifecycle, no deterministic in-flight response, and no cleanup TTL job in the current implementation.

### Error mapping

| Condition | Current HTTP behavior |
|---|---|
| Bean validation failure | `400 Bad Request` with `ErrorResponse.details`. |
| Quota exceeded | `403 Forbidden`. |
| Service-layer runtime failures such as product not found, duplicate code, duplicate slug, or missing tenant context | `500 Internal Server Error`. |
| Unexpected exceptions | `500 Internal Server Error`. |

This is an implementation-accurate contract, not an idealized one.

## 8. Multi-Tenancy, Security, and Observability

- `UserContextFilter` extracts `X-Tenant-Id`, `X-User-Id`, and `X-Roles`, stores them in thread-local context, and writes `tenantId` and `userId` to MDC.
- The filter clears thread-local state and MDC after every request.
- Public HTTP security permits requests at the filter chain level; effective authorization is enforced via method-level `@PreAuthorize`.
- Internal endpoints are intended for trusted callers and are not protected by method-level RBAC.
- Outbox metrics include `outbox_backlog_size`, `outbox_retry_total`, `outbox_publish_latency`, and `outbox_oldest_event_age`.
- OTel tracing is enabled and Kafka producer tracing is stitched through `OtelKafkaProducerInterceptor`.

## 9. Tech Stack

| Layer | Current implementation |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.2 |
| Persistence | Spring Data JPA, PostgreSQL, Flyway |
| Messaging | Kafka producer with transactional outbox |
| Locking | ShedLock 7.6.0 (JDBC) |
| Metrics | Micrometer + Prometheus |
| Tracing | OpenTelemetry OTLP |
| Build | Maven |

## 10. Related Artifacts

| File | Purpose |
|---|---|
| [`api/product.yaml`](api/product.yaml) | OpenAPI contract for public and internal REST endpoints. |
| [`events/product-events.yaml`](events/product-events.yaml) | Published Kafka event contract. |
| [`database/schema.sql`](database/schema.sql) | Consolidated PostgreSQL schema from Flyway migrations. |
| [`dependencies.md`](dependencies.md) | Runtime dependency and header contract summary. |
