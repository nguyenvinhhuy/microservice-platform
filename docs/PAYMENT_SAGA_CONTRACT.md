# Payment Saga Contract

## Purpose
Payment-service participates in the order saga orchestrated by order-service. It processes payments after inventory is reserved and emits payment outcome events for the orchestrator to confirm or compensate the order.

## Inbound Event
payment-service consumes canonical `StockReservedEvent` from Kafka topic `payment.kafka.inventory-topic` (default: `inventory.events`).

Required fields:
- `eventId` (UUID)
- `eventType` = `StockReserved`
- `eventVersion` = `v1`
- `timestamp` (ISO-8601 with offset)
- `correlationId` (string)
- `traceId` (string)
- `orderId` (UUID)
- `tenantId` (number)
- `amount` (number)
- `currency` (string, ISO 4217)
- `paymentProvider` (string)
- `idempotencyKey` (string)

Notes:
- `amount`, `currency`, `paymentProvider`, and `idempotencyKey` are required for safe charging and idempotency. Missing fields are treated as poison messages and routed to DLQ.

## Outbound Events
payment-service emits payment domain events to Kafka topic `payment.kafka.events-topic` (default: `payment-events`) using the Outbox pattern.

Events:
- `PaymentProcessingEvent`
- `PaymentSucceededEvent`
- `PaymentFailedEvent`

## Saga State Mapping
- On `InventoryReservedEvent`:
  - Create or load Payment by `idempotencyKey`.
  - Transition to `PROCESSING` and emit `PaymentProcessingEvent`.
  - Call provider.
  - On success: transition to `SUCCEEDED`, emit `PaymentSucceededEvent`.
  - On provider decline: transition to `FAILED`, emit `PaymentFailedEvent`.
  - On provider timeout: keep `PROCESSING`, throw to trigger retry; if retries exhaust the message is routed to DLQ for manual handling.

## Retry and DLQ
Consumer retry pipeline:
- Main topic: `payment.kafka.inventory-topic`
- Retry topic: `payment.kafka.retry-topic` (default: `payment-events-retry`)
- Dead-letter topic: `payment.kafka.dlq-topic` (default: `payment-events-dlq`)

Routing:
- Failures from main topic are republished to retry topic after bounded in-memory retries.
- Failures from retry topic are republished to DLQ after bounded in-memory retries.
