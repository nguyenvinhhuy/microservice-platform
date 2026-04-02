# Payment Saga Flow

## Goal
Ensure the order saga (orchestrated by order-service) can progress deterministically under retries, duplicates, and partial failures.

## Happy Path
1. order-service reserves stock in inventory-service (REST call).
2. inventory-service emits `StockReservedEvent` to `inventory.events` after commit.
3. payment-service consumes `StockReservedEvent` and:
   - creates/loads `Payment` by `idempotencyKey` (request idempotency).
   - transitions `PENDING -> PROCESSING`.
   - writes `PaymentProcessingEvent` to outbox in the same transaction.
   - charges provider (idempotent provider call using `idempotencyKey`).
   - transitions `PROCESSING -> SUCCEEDED` and writes `PaymentSucceededEvent` to outbox.
4. outbox publisher publishes payment events to Kafka topic `payment-events`.

## Failure Flows
### Provider Decline
- Payment transitions `PROCESSING -> FAILED`.
- `PaymentFailedEvent` is published.

### Provider Timeout
- Payment remains `PROCESSING`.
- Kafka consumer retry triggers re-processing with the same `idempotencyKey`.
- Reconciliation job and saga timeout monitor provide operational safety if stuck.

### Poison Message
- Invalid JSON or missing required fields.
- Routed to DLQ after bounded retries (main -> retry topic -> DLQ).

## Timeout Handling
If a payment remains `PROCESSING` beyond `payment.saga-timeout.minutes`, payment-service marks it as `FAILED` and emits `PaymentFailedEvent` with reason `SAGA_TIMEOUT`.

