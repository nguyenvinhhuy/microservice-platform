# Payment Idempotency

## Goals
Prevent double-charging in the presence of:
- REST retries from clients or upstream services.
- Kafka redeliveries and consumer retries.
- Provider timeouts where the provider may still eventually process the request.

## Request Idempotency (Charging)
Key: `idempotencyKey`

Rules:
- `payments.idempotency_key` is unique.
- If a request arrives with an existing `idempotencyKey`, payment-service returns the existing payment state without creating a new charge.
- If a provider timeout occurs, the payment may remain in `PROCESSING` and will be retried with the same `idempotencyKey`.

## Consumer Idempotency (Kafka)
Table: `processed_events`

Key:
- `(event_id, consumer_service)`

Rules:
- If `(eventId, consumerService)` already exists, the consumer skips processing.
- Otherwise, payment-service processes the event and records the marker in the same transaction.

## Failure Handling
- Invalid messages (missing required fields, invalid JSON) are treated as poison messages and routed to DLQ.
- Provider declines are treated as non-retryable and produce `PaymentFailedEvent`.
- Provider timeouts are treated as retryable and are routed through retry topic; if exhausted they end in DLQ for manual triage.

