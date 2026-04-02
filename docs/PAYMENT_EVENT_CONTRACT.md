# Payment Event Contract

## Versioning
All payment events include:
- `eventType`
- `eventVersion`

The payment-service currently emits `eventVersion = v1` for all payment events.

## Common Fields
All payment events contain:
- `eventId` (UUID)
- `eventType` (string)
- `eventVersion` (string, e.g., `v1`)
- `timestamp` (ISO-8601 with offset)
- `correlationId` (string)
- `traceId` (string)
- `orderId` (UUID)
- `tenantId` (number)
- `paymentId` (UUID)

## Events

### PaymentProcessingEvent (v1)
Indicates the payment is transitioning to `PROCESSING`.

Payload fields:
- Common fields only.

### PaymentSucceededEvent (v1)
Indicates the payment is `SUCCEEDED`.

Payload fields:
- Common fields
- `transactionId` (string)

### PaymentFailedEvent (v1)
Indicates the payment is `FAILED`.

Payload fields:
- Common fields
- `reason` (string)

## Publishing Guarantees
Events are published using the Outbox pattern:
- event is stored to `payment_outbox` in the same transaction as the payment update.
- a scheduled publisher publishes outbox records to Kafka with retries and marks them published.

## Kafka Headers
For trace and correlation propagation, the publisher adds headers when values are available:
- `correlationId`
- `traceId`
- `spanId`
