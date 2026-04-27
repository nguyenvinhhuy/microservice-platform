# inventory-service - Dependency Map

## Runtime Dependencies

| Dependency | Type | Direction | Contract |
|---|---|---|---|
| PostgreSQL (`inventory_db`) | Database | Outbound | Primary persistence for inventory, reservations, and outbox. |
| Apache Kafka | Message broker | Outbound | Publishes reservation and stock update events through `inventory.events`. |
| OTLP collector | Observability | Outbound | Exports traces to `${OTEL_EXPORTER_OTLP_ENDPOINT}`. |

## Internal Libraries

| Library | Version | Purpose |
|---|---|---|
| `event-contract` | `1.0.0` | `BaseEvent<T>`, inventory payload classes, and schema validation. |
| ShedLock | `7.6.0` | Locks the outbox publisher and reservation expiration scheduler. |

## Inbound HTTP

| Endpoint | Caller | Purpose |
|---|---|---|
| `POST /internal/inventory/reservations` | `order-service` or trusted internal caller | Reserve stock for an order. |
| `POST /internal/inventory/reservations/{orderId}/confirm` | `order-service` or trusted internal caller | Confirm a reservation after payment. |
| `POST /internal/inventory/reservations/{orderId}/release` | `order-service` or trusted internal caller | Release a reservation after failure or cancellation. |

## Outbound HTTP

| Dependency | Notes |
|---|---|
| *(none)* | `inventory-service` does not call other services synchronously. |

## Event Publication

| Topic | Event type | Data schema | Trigger |
|---|---|---|---|
| `inventory.events` | `inventory.stock.reserved` | `inventory.stock.reserved.v1` | Successful reserve flow. |
| `inventory.events` | `inventory.stock.confirmed` | `inventory.stock.confirmed.v1` | Successful confirm flow. |
| `inventory.events` | `inventory.stock.released` | `inventory.stock.released.v1` | Successful release flow or expiration release. |
| `inventory.events` | `inventory.stock.updated` | `inventory.stock.updated.v1` | Any stock mutation touched by reserve, confirm, or release. |

Notes:

- Retry and DLQ topics are declared in `KafkaTopicConfig` as `inventory.events.retry` and `inventory.events.dlq`, but the local outbox publisher writes only to `inventory.events`.
- This service has no local Kafka consumers.

## Identity Header Contract

| Header | Required by code | Used by |
|---|---|---|
| `X-Tenant-Id` | Not hard-required by filter, but required by service logic | Tenant scoping and MDC. |
| `X-User-Id` | Optional | MDC and request context only. |

## Persistence Contracts

| Table | Notes |
|---|---|
| `inventory` | Unique on `(tenant_id, product_id)` and uses optimistic locking. |
| `inventory_reservation` | Unique on `(tenant_id, reservation_id)` and `(tenant_id, order_id)`. |
| `inventory_reservation_item` | Reservation line items. |
| `outbox_events` | Stores immutable event envelopes for Kafka publication. |
| `shedlock` | Lock table for scheduler coordination. |
| `processed_events` | Provisioned but unused by this service. |
