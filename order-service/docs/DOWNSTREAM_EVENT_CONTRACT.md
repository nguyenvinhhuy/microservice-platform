# Downstream Event Contract

## Topic
- `order.events`

## Delivery Model
- Source of truth: table `outbox_events`
- Delivery semantics: at-least-once
- Ordering key: `aggregateId` (order id string)

## Kafka Headers
- `eventId`: UUID string
- `eventType`: event class semantic type
- `correlationId`: end-to-end correlation key
- `causationId`: producer-side causation key
- `idempotencyKey`: effective business idempotency key captured from the originating command API. This may come from `Idempotency-Key` or, for remaining backward-compatible command paths only, legacy `X-Request-Id`.

## Payload Format
- Outbox payload is serialized JSON string.
- Payload values correspond to event DTOs in package `huynv.orderservice.event`:
  - `OrderCreatedEvent`
  - `OrderPaidEvent`
  - `OrderCancelledEvent`
  - `OrderFailedEvent`

## Event Versioning
- Field: `eventVersion`
- Current version: `1`
- Rule: downstream consumers must ignore unknown fields.

## Consumer Contract
- Consumers must be idempotent by `eventId`.
- Consumers must tolerate duplicate deliveries.
- Consumers should persist processed-event ids for deduplication.
