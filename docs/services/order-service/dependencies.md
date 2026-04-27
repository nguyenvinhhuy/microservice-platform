# order-service - Dependency Map

## Runtime Dependencies

| Dependency | Type | Direction | Contract |
|---|---|---|---|
| PostgreSQL (`order_db`) | Database | Outbound | Primary persistence for orders, idempotency, sagas, payments, and outbox. |
| Apache Kafka | Message broker | Outbound | Publishes order integration events through `order.events`. |
| Redis | Cache / rate limiting | Optional outbound | Used only when `spring.data.redis.enabled=true` for create-order token bucket limiting. |
| OTLP collector | Observability | Outbound | Exports traces to `${OTEL_EXPORTER_OTLP_ENDPOINT}`. |
| `inventory-service` | HTTP | Outbound | Reserve, confirm, and release inventory. |
| `payment-service` | HTTP | Outbound | Charge payments synchronously. |
| `product-service` | HTTP | Outbound | Load product snapshots during create-order validation. |

## Internal Libraries

| Library | Version | Purpose |
|---|---|---|
| `event-contract` | `1.0.0` | `BaseEvent<T>`, order payload classes, schema validation, and envelope factory. |
| ShedLock | `6.3.1` | Locks scheduled outbox and saga-resume jobs. |
| Resilience4j | `2.3.0` | Circuit breaker, retry, bulkhead, and time limiter around outbound HTTP clients. |

## Inbound HTTP

| Endpoint | Purpose |
|---|---|
| `POST /orders` | Create command. |
| `POST /orders/{orderId}/pay` | Pay command. |
| `POST /orders/{orderId}/cancel` | Cancel command. |

## Outbound HTTP

| Dependency | Endpoint | Notes |
|---|---|---|
| `inventory-service` | `POST /internal/inventory/reservations` | Reserve stock. |
| `inventory-service` | `POST /internal/inventory/reservations/{orderId}/confirm` | Confirm stock after charge succeeds. |
| `inventory-service` | `POST /internal/inventory/reservations/{orderId}/release` | Release stock on cancel or compensation. |
| `payment-service` | `POST /api/payments` | Charge payment using required `Idempotency-Key`; `X-Request-Id` remains tracing-only for this downstream REST contract. |
| `product-service` | `GET /internal/products/{id}` | Load product price and currency for create-order validation. |

## Event Publication

| Topic | Event type | Data schema | Trigger |
|---|---|---|---|
| `order.events` | `order.created` | `order.created.v1` | Reservation succeeded and order moved to `RESERVED`. |
| `order.events` | `order.paid` | `order.paid.v1` | Payment and confirm succeeded and order moved to `CONFIRMED`. |
| `order.events` | `order.failed` | `order.failed.v1` | Reservation or payment flow failed. |
| `order.events` | `order.cancelled` | `order.cancelled.v1` | Cancel command succeeded. |

Notes:

- Retry and DLQ topics are declared in `KafkaConfig` as `order.events.retry` and `order.events.dlq`, but the local outbox publisher writes only to `order.events`.
- This service has no local Kafka consumers.

## Identity Header Contract

| Header | Required | Used by |
|---|---|---|
| `X-Tenant-Id` | Yes | Tenant scoping, MDC, security context. |
| `X-User-Id` | Yes | Ownership and security context. |
| `X-Roles` | Yes | Method-level authorization. |
| `Idempotency-Key` | Canonical on write APIs | Business command deduplication for create, pay, and cancel. |
| `X-Request-Id` | No | MDC and transport tracing; accepted only as a temporary compatibility fallback for create, pay, and cancel. |
| `X-Correlation-Id` | No | MDC correlation; falls back to `X-Request-Id` when absent. |

## Persistence Contracts

| Table | Notes |
|---|---|
| `orders` | Optimistically locked aggregate root. |
| `order_items` | Immutable purchase snapshot embedded under the order. |
| `order_payments` | Payment snapshot per order with status values `INITIATED`, `SUCCESS`, or `FAILED`. |
| `idempotency_keys` | Stores request lifecycle and cached response JSON. |
| `order_sagas` | Persisted orchestration state with retry counter and payment metadata. |
| `outbox_events` | Stores immutable event envelopes for Kafka publishing. |
| `shedlock` | Scheduler lock table. |
| `processed_events` | Provisioned but unused by this service. |
