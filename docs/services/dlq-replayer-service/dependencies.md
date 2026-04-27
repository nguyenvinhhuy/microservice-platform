# DLQ Replayer Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | JDBC idempotency support. |
| Spring Web | Admin REST API. |
| Spring Data JPA | DLQ record persistence. |
| Spring Kafka | Consume DLQ topics and replay records to Kafka. |
| PostgreSQL | Storage for DLQ events. |
| Flyway | Schema migration management. |
| Micrometer / OpenTelemetry | Metrics and tracing. |

## Inbound Interfaces

### REST

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/dlq/events` | List stored DLQ events filtered by status. |
| `POST` | `/dlq/replay` | Replay one stored DLQ event. |
| `POST` | `/dlq/skip` | Skip one stored DLQ event. |

### Kafka

| Topic Pattern | Consumer | Purpose |
| --- | --- | --- |
| `.*\.dlq` | `DlqConsumer` | Persist dead-letter records for inspection/replay. |

## Outbound Interfaces

### Kafka

| Publisher | Purpose |
| --- | --- |
| `DlqReplayService` via `KafkaTemplate<String, String>` | Republish stored payloads to original or override topic. |

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `dlq_events` | Stored DLQ records for inspection and replay. |
| `processed_events` | Consumer idempotency markers for DLQ ingestion. |

## Configuration Surface

| Key | Default | Purpose |
| --- | --- | --- |
| `dlq.topic-pattern` | `.*\\.dlq` | Kafka topic regex for DLQ ingestion. |
| `dlq.consumer.group-id` | `dlq-replayer-service` | Consumer group id. |

## Notable Constraints

- The service is operational tooling, not tenant-scoped business logic.
- Replay republishes raw payload and key only.
- Admin API is not guarded by module-local security configuration.
