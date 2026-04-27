# inventory-service

## 1. Purpose

`inventory-service` owns tenant-scoped stock reservation and stock finalization for order orchestration. It exposes internal HTTP endpoints used by `order-service`, persists reservation state, and publishes inventory events through a transactional outbox.

## 2. Key Functions

| Function | Current implementation contract |
|---|---|
| Reserve stock | `POST /internal/inventory/reservations` creates a `RESERVED` reservation and atomically increments `reservedStock` when enough stock is available. |
| Confirm stock | `POST /internal/inventory/reservations/{orderId}/confirm` finalizes a reservation by decrementing both `totalStock` and `reservedStock`, then marks the reservation `CONFIRMED`. |
| Release stock | `POST /internal/inventory/reservations/{orderId}/release` decrements `reservedStock` and marks the reservation `RELEASED`. |
| Expiration handling | A scheduled job releases expired `RESERVED` reservations based on `expiresAt`. |
| Producer outbox | Reservation and stock snapshot events are written to `outbox_events` and published asynchronously to Kafka. |
| Tenant ownership | All reservation and inventory lookups used by service logic are tenant-aware. |

## 3. API Surface

| Endpoint | Method | Caller contract | Notes |
|---|---|---|---|
| `/internal/inventory/reservations` | `POST` | Trusted internal caller | Returns `204 No Content` on success or idempotent replay. |
| `/internal/inventory/reservations/{orderId}/confirm` | `POST` | Trusted internal caller | Returns `204 No Content`; idempotent when already confirmed. |
| `/internal/inventory/reservations/{orderId}/release` | `POST` | Trusted internal caller | Returns `204 No Content`; idempotent when already released, no-op when already confirmed. |

## 4. Domain Rules

| Rule | Current implementation contract |
|---|---|
| Reservation uniqueness | `inventory_reservation` is unique on `(tenant_id, order_id)` and `(tenant_id, reservation_id)`. |
| Reserve idempotency | If a reservation already exists for `(tenantId, orderId)`, reserve returns success without mutating stock again. |
| Atomic reserve update | Stock reservation uses a single update query that increments `reservedStock` only when `(totalStock - reservedStock) >= quantity`. |
| Confirm allowed state | Only `RESERVED` reservations can be confirmed. |
| Release allowed state | Only `RESERVED` reservations are actively released. `RELEASED` is idempotent and `CONFIRMED` is treated as a no-op. |
| Tenant safety | Missing inventory rows for requested product ids are treated as tenant ownership violations. |
| Expiration policy | New reservations expire at `now + inventory.reservation.expiration`, default `PT10M`. |
| Kill switch | All three HTTP reservation endpoints fail when `feature.inventory.reservation.enabled=false`. |

## 5. State and Flow Contracts

### Reservation status lifecycle

| Status | Meaning |
|---|---|
| `RESERVED` | Stock is held for an order and awaits confirm or release. |
| `CONFIRMED` | Reservation is finalized and stock is consumed. |
| `RELEASED` | Reservation is reversed and stock is returned to availability. |

### Create reservation flow

1. Load `tenantId` from `UserContext`.
2. If a reservation already exists for `(orderId, tenantId)`, return success without side effects.
3. Load all inventory rows for requested product ids using a tenant-scoped query.
4. Atomically increment `reservedStock` per item using `reserveStockIfAvailable`.
5. Persist `inventory_reservation` and `inventory_reservation_item`.
6. Enqueue `inventory.stock.reserved`.
7. Enqueue one `inventory.stock.updated` event per touched product.

### Confirm flow

1. Load the reservation by `(orderId, tenantId)`.
2. If already `CONFIRMED`, return success.
3. Reject any state other than `RESERVED`.
4. Decrement both `totalStock` and `reservedStock` for each item.
5. Mark the reservation `CONFIRMED`.
6. Enqueue `inventory.stock.confirmed`.
7. Enqueue one `inventory.stock.updated` event per touched product.

### Release flow

1. Load the reservation by `(orderId, tenantId)`.
2. If already `RELEASED`, return success.
3. If already `CONFIRMED`, return success without reversing stock.
4. Reject any state other than `RESERVED`.
5. Decrement `reservedStock` for each item.
6. Mark the reservation `RELEASED`.
7. Enqueue `inventory.stock.released`.
8. Enqueue one `inventory.stock.updated` event per touched product.

## 6. Event Flows

### Outbound events

| Event type | Topic | Trigger | Payload |
|---|---|---|---|
| `inventory.stock.reserved` | `inventory.events` | Successful reserve flow | `StockReservedEvent` |
| `inventory.stock.confirmed` | `inventory.events` | Successful confirm flow | `StockConfirmedEvent` |
| `inventory.stock.released` | `inventory.events` | Successful release flow or expiration release | `StockReleasedEvent` |
| `inventory.stock.updated` | `inventory.events` | Any reserve, confirm, or release mutation touching stock | `StockUpdatedEvent` |

### Publication model

- Events are written to `outbox_events` in the same transaction as reservation or inventory mutation.
- `InventoryOutboxPublisher` claims `PENDING` and `FAILED` rows with `FOR UPDATE SKIP LOCKED`.
- Kafka topic is `inventory.events`.
- Declared retry and DLQ topics are `inventory.events.retry` and `inventory.events.dlq`, but the local publisher sends directly to `inventory.events`.
- Envelope `eventType` values are semantic names such as `inventory.stock.reserved`.
- Envelope `dataSchema` values are versioned schema ids such as `inventory.stock.reserved.v1`.
- Failed publishes use exponential backoff: `min(60, 2^min(retryCount, 6))` seconds.
- This service has no local Kafka consumers. `processed_events` is provisioned for future use only.

## 7. Persistence and Background Jobs

| Table | Purpose |
|---|---|
| `inventory` | Stock snapshot per `(tenantId, productId)`. |
| `inventory_reservation` | Reservation aggregate per `(tenantId, orderId)`. |
| `inventory_reservation_item` | Reservation line items. |
| `outbox_events` | Transactional outbox for Kafka publication. |
| `shedlock` | Distributed lock table. |
| `processed_events` | Reserved for future consumer-side idempotency. |

Scheduled jobs:

- `InventoryOutboxPublisher.publishDueOutbox()` runs on `${inventory.outbox.publisher-delay-ms:2000}` with ShedLock name `inventory-service-outbox-publisher`.
- `ReservationExpirationScheduler.releaseExpiredReservations()` runs on `${inventory.reservation.expiration-check-interval}`, default `60000`, with ShedLock name `releaseExpiredReservations`.
- `OutboxMonitoringMetrics.refreshOldestAge()` runs on `${inventory.outbox.metrics.interval-ms:15000}`.

## 8. Error and Security Semantics

### Error mapping

| Condition | Current HTTP behavior |
|---|---|
| Insufficient stock | `400 Bad Request` |
| Invalid reservation status | `400 Bad Request` |
| Reservation not found | `404 Not Found` |
| Tenant ownership violation | `403 Forbidden` |
| Optimistic locking or explicit concurrent update conflict | `409 Conflict` |
| Kill switch or other uncaught runtime failure | Falls through as generic framework error because no local handler exists for `IllegalStateException`. |

### Security and context

- `UserContextFilter` reads `X-Tenant-Id` and `X-User-Id`.
- Missing `X-Tenant-Id` is only logged, not rejected by the filter.
- All non-actuator endpoints require authentication at the security filter chain level.
- The service relies on trusted internal callers and request headers for tenant scoping.
- MDC keys `tenantId`, `userId`, `orderId`, and `productId` are cleared after each request and after service operations.

## 9. Observability

- Reservation metrics use counters such as `inventory.reservation` and `inventory.state.change`.
- Reservation latency is recorded in `inventory_reservation_latency`.
- Outbox metrics include `outbox_backlog_size`, `outbox_retry_total`, `outbox_publish_latency`, and `outbox_oldest_event_age`.
- OTel tracing is enabled and the Kafka producer uses `OtelKafkaProducerInterceptor`.

## 10. Tech Stack

| Layer | Current implementation |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Persistence | Spring Data JPA, PostgreSQL, Flyway |
| Messaging | Kafka producer with transactional outbox |
| Locking | Optimistic locking on `inventory`, ShedLock 7.6.0 |
| Metrics | Micrometer + Prometheus |
| Tracing | OpenTelemetry OTLP |
| Build | Maven |

## 11. Related Artifacts

| File | Purpose |
|---|---|
| [`api/inventory.yaml`](api/inventory.yaml) | OpenAPI contract for internal reservation endpoints. |
| [`events/inventory-events.yaml`](events/inventory-events.yaml) | Published Kafka event contract. |
| [`database/schema.sql`](database/schema.sql) | Consolidated PostgreSQL schema from Flyway migrations. |
| [`dependencies.md`](dependencies.md) | Runtime dependency and header contract summary. |
