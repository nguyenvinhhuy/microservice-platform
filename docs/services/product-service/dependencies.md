# product-service - Dependency Map

## Runtime Dependencies

| Dependency | Type | Direction | Contract |
|---|---|---|---|
| PostgreSQL (`product_db`) | Database | Outbound | Primary persistence for catalog tables, idempotency, and outbox. |
| Apache Kafka | Message broker | Outbound | Publishes `product.updated` and `product.price.updated` through `product.events`. |
| OTLP collector | Observability | Outbound | Exports traces to `${OTEL_EXPORTER_OTLP_ENDPOINT}`. |

## Internal Libraries

| Library | Version | Purpose |
|---|---|---|
| `event-contract` | `1.0.0` | `BaseEvent<T>`, `EventFactory`, product payload classes, and JSON schemas. |
| ShedLock | `7.6.0` | Distributed lock for `ProductOutboxPublisher`. |

## Inbound HTTP

| Endpoint | Caller class | Purpose |
|---|---|---|
| `POST /api/v1/products` | Gateway or trusted edge | Create product. |
| `GET /api/v1/products` | Gateway or trusted edge | Paginated tenant-scoped listing. |
| `GET /api/v1/products/{id}` | Gateway or trusted edge | Tenant-scoped lookup by id. |
| `GET /api/v1/products/code/{code}` | Gateway or trusted edge | Tenant-scoped lookup by code. |
| `GET /api/v1/products/slug/{slug}` | Gateway or trusted edge | Tenant-scoped lookup by slug. |
| `PUT /api/v1/products/{id}` | Gateway or trusted edge | Full product update. |
| `DELETE /api/v1/products/{id}` | Gateway or trusted edge | Soft delete. |
| `GET /api/v1/products/search` | Gateway or trusted edge | Keyword search. |
| `GET /api/v1/products/status/{status}` | Gateway or trusted edge | Paginated status filter. |
| `GET /api/v1/products/status-list/{status}` | Gateway or trusted edge | List status filter. |
| `GET /api/v1/products/category/{categoryId}` | Gateway or trusted edge | Category filter. |
| `GET /api/v1/products/price-range` | Gateway or trusted edge | Price range filter. |
| `GET /internal/products/{id}` | Trusted internal services | Synchronous product lookup for orchestration. |

## Outbound HTTP

| Dependency | Notes |
|---|---|
| *(none)* | `product-service` makes no synchronous outbound HTTP calls. `PlanService` is in-process. |

## Event Publication

| Topic | Event type | Data schema | Trigger |
|---|---|---|---|
| `product.events` | `product.updated` | `product.updated.v1` | Every successful update. |
| `product.events` | `product.price.updated` | `product.price.updated.v1` | Update where `price` or `currency` changed. |

Notes:

- Retry and DLQ topics are declared in `KafkaTopicConfig` as `product.events.retry` and `product.events.dlq`, but the local outbox publisher writes only to `product.events`.
- This service has no local Kafka consumers.

## Identity Header Contract

| Header | Required | Used by |
|---|---|---|
| `X-Tenant-Id` | Yes | Tenant scoping, MDC, service rules. |
| `X-User-Id` | Optional for public docs, tolerated by filter | User context and auditing. |
| `X-Roles` | Yes | Method-level authorization and quota plan derivation (`ROLE_PRO`). |
| `Idempotency-Key` | Optional on create only | Canonical producer-side idempotency header for `POST /api/v1/products`. |
| `X-Request-Id` | Optional | Request tracing header and temporary compatibility fallback for create idempotency only. |
| `X-Correlation-Id` | Not consumed by local filter | Not part of the current product-service request filter contract. |

## Persistence Contracts

| Table | Notes |
|---|---|
| `t_products` | Main aggregate with global unique constraints on `code` and `slug`. |
| `idempotency_keys` | Stores only request UUID, response status, optional response body, and created timestamp. |
| `outbox_events` | Stores immutable envelopes for at-least-once Kafka publishing. |
| `shedlock` | Lock table for scheduled publisher execution. |
| `processed_events` | Provisioned but unused by this service. |
