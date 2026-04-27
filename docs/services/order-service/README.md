# order-service

## 1. Purpose

`order-service` is the command-side owner of order placement. It validates create and pay commands, orchestrates the order saga, persists command idempotency, and publishes order integration events through a transactional outbox.

## 2. Key Functions

| Function | Current implementation contract |
|---|---|
| Create order | `POST /orders` validates request context, optionally normalizes prices from `product-service`, persists a `CREATED` order, reserves stock synchronously, and returns `201` only after the reservation step succeeds. |
| Pay order | `POST /orders/{orderId}/pay` starts or resumes the charge-and-confirm flow. The service charges payment first, stores `paymentId` in saga state, then confirms stock. |
| Cancel order | `POST /orders/{orderId}/cancel` releases reservation when the order is `RESERVED` or `PAYMENT_IN_PROGRESS`, then marks the order `CANCELLED`. |
| Saga orchestration | `OrderSagaCoordinator` persists one saga row per `(tenantId, orderId)` and drives states `RESERVE_STOCK -> CHARGE_PAYMENT -> CONFIRM_STOCK -> COMPLETED | COMPENSATING`. |
| Crash-safe resume | `resumeInFlightSagas()` replays `RESERVE_STOCK`, `CONFIRM_STOCK`, and `COMPENSATING` states on a scheduler with ShedLock protection. |
| Request idempotency | All public commands use `Idempotency-Key` as the canonical business deduplication header. `X-Request-Id` remains the tracing header and is accepted only as a temporary backward-compatible fallback while callers migrate. State is stored in `idempotency_keys` with `PROCESSING`, `COMPLETED`, or `FAILED`. |
| Producer outbox | Domain events are written to `outbox_events` and published by `OrderEventProducer` with at-least-once semantics. |
| Rate limiting | `POST /orders` can be rate-limited by `OrderCreateRateLimitFilter` when Redis support is enabled. |

## 3. API Surface

| Endpoint | Method | Role contract | Notes |
|---|---|---|---|
| `/orders` | `POST` | `ROLE_USER` | `Idempotency-Key` is the canonical command header. `X-Request-Id` is accepted only as a temporary compatibility fallback. Successful response means reservation already succeeded. |
| `/orders/{orderId}/pay` | `POST` | `ROLE_USER` | `Idempotency-Key` is the canonical command header. `X-Request-Id` is accepted only as a temporary compatibility fallback. Returns deterministic in-flight replay when another pay command is already processing. |
| `/orders/{orderId}/cancel` | `POST` | `ROLE_ADMIN` | `Idempotency-Key` is the canonical command header. `X-Request-Id` is accepted only as a temporary compatibility fallback. |

## 4. State and Flow Contracts

### Order status lifecycle

| Status | Meaning |
|---|---|
| `CREATED` | Order row persisted, stock not yet reserved. |
| `RESERVED` | Inventory reservation succeeded and `order.created` was enqueued. |
| `PAYMENT_IN_PROGRESS` | Payment flow has started and payment is being charged or resumed. |
| `CONFIRMED` | Payment succeeded and inventory confirm succeeded. |
| `FAILED` | Reservation or payment flow failed. |
| `CANCELLED` | Order was cancelled by API command. |
| `COMPENSATING` | Rollback is in progress because a later step failed after partial side effects. |

### Saga state lifecycle

| Saga state | Meaning |
|---|---|
| `RESERVE_STOCK` | Next step is inventory reservation. |
| `CHARGE_PAYMENT` | Next step is payment charge. |
| `CONFIRM_STOCK` | Payment already succeeded; next step is inventory confirm. |
| `COMPLETED` | Terminal saga state. |
| `COMPENSATING` | Refund and release logic is being retried. |

### Command flow summary

1. `POST /orders`
   The service validates tenant and user context, optionally normalizes item prices from `product-service`, creates the order, binds the idempotency row to `orderId`, executes inventory reservation, marks the order `RESERVED`, enqueues `order.created`, and returns `201`.
2. `POST /orders/{orderId}/pay`
   The service starts or resumes a pay saga, moves the order to `PAYMENT_IN_PROGRESS`, charges `payment-service`, stores `paymentId` in saga state, confirms inventory, marks the order `CONFIRMED`, enqueues `order.paid`, and returns `200`.
3. `POST /orders/{orderId}/cancel`
   The service optionally releases inventory, marks the order `CANCELLED`, enqueues `order.cancelled`, and returns `200`.

## 5. Business Rules

| Rule | Current implementation contract |
|---|---|
| Mandatory idempotency | All three public commands standardize on `Idempotency-Key`; `X-Request-Id` is still accepted only as a temporary compatibility fallback. |
| Trusted identity headers | `X-Tenant-Id`, `X-User-Id`, and `X-Roles` are mandatory on all non-actuator requests. |
| Product validation switch | `feature.order-product-validation.enabled=true` causes create requests to call `product-service` and overwrite item prices from the authoritative product snapshot. |
| Currency match | Create fails when request currency and product currency differ. |
| Successful create semantics | A create command is considered successful only after inventory reservation succeeds. |
| Reservation failure cleanup | When reservation fails, the order may be transitioned to `FAILED`, an `order.failed` event is enqueued, and the unreserved order row is deleted. |
| Payment crash safety | `paymentId` is persisted in saga state before stock confirmation so compensation can resume safely. |
| Cancel rule | Cancelling a `CONFIRMED` order throws `DomainInvariantViolationException` and maps to HTTP 409. |
| Concurrency | `Order` and `OrderSaga` use optimistic locking. `beginPayment()` also uses a pessimistic lookup on the reserved row. |

## 6. Event Flows

### Outbound events

| Event type | Topic | Trigger | Payload class |
|---|---|---|---|
| `order.created` | `order.events` | Reservation succeeded and order moved to `RESERVED` | `OrderCreatedEvent` |
| `order.paid` | `order.events` | Payment and confirm both succeeded and order moved to `CONFIRMED` | `OrderPaidEvent` |
| `order.failed` | `order.events` | Reservation or payment flow failed and order moved to `FAILED` | `OrderFailedEvent` |
| `order.cancelled` | `order.events` | Cancel command succeeded and order moved to `CANCELLED` | `OrderCancelledEvent` |

### Publication model

- Outbox writes happen in the same transaction as order state mutation.
- `OrderEventProducer` claims due rows from `outbox_events` with `FOR UPDATE SKIP LOCKED`.
- Kafka key is the aggregate id, currently `orderId`.
- Envelope `eventType` values are unversioned semantic names such as `order.created`.
- Envelope `dataSchema` values are versioned names such as `order.created.v1`.
- Failed publishes use exponential backoff: `min(60, 2^min(retryCount, 6))` seconds.
- The service has no Kafka listeners. `processed_events` is reserved for future use only.

## 7. Persistence and Background Jobs

| Table | Purpose |
|---|---|
| `orders` | Main order aggregate root. |
| `order_items` | Immutable line items stored with the order. |
| `order_payments` | Payment snapshot per order. |
| `idempotency_keys` | Command idempotency state and cached response payload. |
| `outbox_events` | Transactional outbox for Kafka publication. |
| `order_sagas` | Persisted saga state for resume and compensation. |
| `shedlock` | Distributed lock table for scheduled jobs. |
| `processed_events` | Reserved for future consumer-side idempotency. |

Scheduled jobs:

- `OrderEventProducer.publishOutboxBatch()` runs on `${outbox.publisher.delay-ms:1000}` with ShedLock name `order-service-outbox-publisher`.
- `OrderSagaCoordinator.resumeInFlightSagas()` runs on `${saga.resume.delay-ms:5000}` with ShedLock name `order-service-saga-resume`.
- `OutboxMonitoringMetrics.refreshOldestAge()` runs on `${outbox.metrics.interval-ms:15000}`.

## 8. Error and Idempotency Semantics

### Idempotency

- `idempotency_keys` is unique on `(tenant_id, request_id, api_name)`, where `request_id` stores the effective command idempotency key.
- `begin()` creates a `PROCESSING` row or returns the existing row on duplicates.
- `bindOrder()` stores `orderId` early so in-flight retries can return deterministic responses.
- `complete()` stores serialized terminal payload and marks the row `COMPLETED`.
- `fail()` stores serialized terminal payload and marks the row `FAILED`.

Current replay behavior:

- Create in-flight replay returns `{ orderId, status: "PROCESSING" }`.
- Pay and cancel in-flight replay return the current order status with messages `PAYMENT_PROCESSING` or `CANCEL_PROCESSING`.
- Completed and failed commands replay the stored JSON payload.

Tracing note:

- `X-Request-Id` is now treated as the transport-level request tracing header and is copied into MDC as `requestId`.
- `X-Correlation-Id` remains the cross-service flow identifier and falls back to `X-Request-Id` only when no explicit correlation id exists.
- `Idempotency-Key` is the canonical business deduplication header for public write APIs.
- `X-Request-Id` fallback support exists only for backward compatibility and should not be used by new callers.

### Error mapping

| Condition | Current HTTP behavior |
|---|---|
| Bean validation or invalid identity headers | `400 Bad Request` |
| `OrderNotFoundException` | `404 Not Found` |
| Invalid state, domain invariant violation, inventory reservation failure, optimistic lock conflict | `409 Conflict` |
| Payment failure | `402 Payment Required` |
| Downstream service unavailable | `503 Service Unavailable` |
| Unexpected exception | `500 Internal Server Error` |

## 9. Multi-Tenancy, Security, and Observability

- `UserContextFilter` enforces presence of `X-Tenant-Id`, `X-User-Id`, and `X-Roles` for all non-actuator requests.
- The filter populates thread-local context, Spring Security authentication, and MDC keys `tenantId`, `userId`, `requestId`, `correlationId`, and later `orderId`.
- Context and MDC are cleared after every request.
- Public HTTP security requires authentication for non-actuator endpoints. Effective roles come from trusted gateway headers.
- Metrics include `orders_created_total`, `orders_failed_total`, `order_inventory_failed_total`, `payment.charge.latency`, `inventory.reserve.latency`, `outbox_backlog_size`, `outbox_retry_total`, `outbox_publish_latency`, and `outbox_oldest_event_age`.
- Kafka producer tracing is stitched through OTel spans and synthetic parent span ids.

## 10. Tech Stack

| Layer | Current implementation |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.2 |
| Persistence | Spring Data JPA, PostgreSQL, Flyway |
| Messaging | Kafka producer with transactional outbox |
| Locking | Optimistic locking, selective pessimistic locking, ShedLock 6.3.1 |
| Resilience | Resilience4j circuit breaker, retry, bulkhead, and time limiter |
| Caching / rate limiting | Optional Redis-backed token bucket for create-order |
| Metrics | Micrometer + Prometheus |
| Tracing | OpenTelemetry OTLP |
| Build | Maven |

## 11. Related Artifacts

| File | Purpose |
|---|---|
| [`api/order.yaml`](api/order.yaml) | OpenAPI contract for public REST commands. |
| [`events/order-events.yaml`](events/order-events.yaml) | Published Kafka event contract. |
| [`database/schema.sql`](database/schema.sql) | Consolidated PostgreSQL schema from Flyway migrations. |
| [`dependencies.md`](dependencies.md) | Runtime dependency and header contract summary. |
