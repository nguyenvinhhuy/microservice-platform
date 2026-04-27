# Payment Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | `BaseEvent`, event payload classes, schema validation, idempotency abstraction. |
| Spring Web | Expose REST API for payment processing and querying. |
| Spring Data JPA | Persist `Payment`, `PaymentOutbox`, `ProcessedEvent`. |
| Spring Kafka | Consume inventory events and publish payment events. |
| PostgreSQL | Primary transactional store. |
| Flyway | Schema migration management. |
| Resilience4j Retry | Retry timeout-prone provider charge attempts. |
| ShedLock | Single-node execution for outbox and timeout/reconciliation jobs. |
| Micrometer / Prometheus | Metrics export. |
| OpenTelemetry | Tracing and Kafka/HTTP instrumentation. |

## Inbound Interfaces

### REST

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/payments` | Process one payment command idempotently by required `Idempotency-Key` header. |
| `GET` | `/api/payments/{paymentId}` | Read one payment aggregate by identifier. |

Header semantics for `POST /api/payments`:

- `Idempotency-Key` is the canonical and only REST business deduplication header.
- `X-Request-Id` may still exist in upstream tracing, but it is not part of the payment REST idempotency contract.
- Request-body `idempotencyKey` is no longer part of the REST request schema.

### Kafka

| Topic | Condition | Payload | Purpose |
| --- | --- | --- | --- |
| `inventory.events` | `payment.kafka.consumer.enabled=true` | `BaseEvent<StockReservedEvent>` | Main saga trigger after stock reservation. |
| `inventory.events.retry` | `payment.kafka.consumer.enabled=true` | `BaseEvent<StockReservedEvent>` | Retry path for transient consumer failures. |

## Outbound Interfaces

### Kafka

| Topic | Payload | Source |
| --- | --- | --- |
| `payment.events` | `BaseEvent<PaymentProcessingEvent>` | `PaymentEventProducer.enqueueProcessing()` |
| `payment.events` | `BaseEvent<PaymentCompletedEvent>` | `PaymentEventProducer.enqueueSucceeded()` |
| `payment.events` | `BaseEvent<PaymentFailedEvent>` | `PaymentEventProducer.enqueueFailed()` |

### Retry and DLQ Topology

Inbound consumer routing:

- source: `inventory.events`
- retry: `inventory.events.retry`
- dlq: `inventory.events.dlq`

Outbound topic beans declared by config:

- `payment.events`
- `payment.events.retry`
- `payment.events.dlq`

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `payments` | Payment aggregate state and request idempotency key. |
| `payment_outbox` | Reliable outbound event publishing state. |
| `processed_events` | Consumer idempotency markers. |
| `shedlock` | Distributed scheduler locks. |

## Externalized Configuration

| Key | Default | Purpose |
| --- | --- | --- |
| `payment.processing.enabled` | `true` | Kill switch for payment processing. |
| `payment.outbox.enabled` | `true` | Enable scheduled outbox publishing. |
| `payment.outbox.publisher-delay-ms` | `2000` | Outbox polling delay. |
| `payment.outbox.publisher-batch-size` | `50` | Outbox batch size. |
| `payment.kafka.inventory-topic` | `inventory.events` | Main inbound inventory topic. |
| `payment.kafka.retry-topic` | `inventory.events.retry` | Retry inbound topic. |
| `payment.kafka.dlq-topic` | `inventory.events.dlq` | DLQ inbound topic. |
| `payment.kafka.events-topic` | `payment.events` | Outbound payment topic. |
| `payment.kafka.consumer.enabled` | `false` | Enable Kafka consumer. |
| `payment.provider.simulated.enabled` | `true` | Enable simulated provider client. |
| `payment.reconciliation.interval-ms` | `60000` | Reconciliation scheduler interval. |
| `payment.reconciliation.processing-cutoff-minutes` | `5` | Reconciliation staleness cutoff. |
| `payment.saga-timeout.interval-ms` | `60000` | Timeout monitor interval. |
| `payment.saga-timeout.minutes` | `15` | Timeout failure cutoff. |

## Notable Implementation Constraints

- REST idempotency is implemented via required `Idempotency-Key` mapped to `payments.idempotency_key`, not a dedicated idempotency table.
- The REST controller does not accept request-body `idempotencyKey` and does not treat `X-Request-Id` as a fallback deduplication source.
- Consumer idempotency is scoped by a single-table `processed_events` design where `event_id` is the primary key.
- `GET /api/payments/{paymentId}` is not tenant-filtered in the repository call.
