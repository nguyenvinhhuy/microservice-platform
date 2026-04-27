# event-infra Topics And Tables

## Kafka Topics
### Declared By `KafkaConsumerConfig`

| Topic | Default value | Partitions | Used by |
| --- | --- | --- | --- |
| Internal events | `notification.events` | 24 | Durable ingestion buffer and retry target for upstream order/payment events. |
| Retry 1m | `notification.retry.1m` | 24 | First retry tier for short delay failures. |
| Retry 5m | `notification.retry.5m` | 24 | Second retry tier for medium delay failures. |
| Retry 30m | `notification.retry.30m` | 24 | Third retry tier for long delay failures. |
| DLQ | `notification.dlq` | 24 | Poison message isolation and operator replay source. |
| High priority | `notification.high` | 12 | Dispatcher ingress for high-priority jobs. |
| Normal priority | `notification.normal` | 12 | Dispatcher ingress for normal-priority jobs. |
| Low priority | `notification.low` | 12 | Dispatcher ingress for low-priority jobs. |
| Email worker | `notification.email` | 12 | Channel-specific email work queue. |
| SMS worker | `notification.sms` | 12 | Channel-specific SMS work queue. |
| Push worker | `notification.push` | 12 | Channel-specific push work queue. |

## Retry Header Contract
The retry and replay pipeline depends on the following headers:

| Header | Producer | Meaning |
| --- | --- | --- |
| `attempt` | `KafkaOutboxRecoverer` | Retry attempt number for the record. |
| `first_seen_at_ms` | `KafkaOutboxRecoverer` | Timestamp of the first observed failure. |
| `retry_due_at_ms` | `KafkaOutboxRecoverer` | Earliest time when `RetryDelayConsumer` should forward the record. |
| `retry_target_topic` | `KafkaOutboxRecoverer` | Topic that retry consumers should republish to. |
| `original_topic` | recoverer and replay path | Original source topic for diagnostics and replay. |
| `original_partition` | `KafkaOutboxRecoverer` | Original Kafka partition. |
| `original_offset` | `KafkaOutboxRecoverer` | Original Kafka offset. |
| `error_class` | `KafkaOutboxRecoverer` | Failure class name. |
| `error_message` | `KafkaOutboxRecoverer` | Truncated failure message. |
| `x-replay-count` | `DlqReplayService` | Number of DLQ replay attempts already performed. |
| `dlq_replay_id` | `DlqReplayService` | Idempotency marker for replayed records. |

## Database Shape Expected By The Library
### `kafka_outbox`
The entity `KafkaOutboxMessage` maps the following columns:

| Column | Type expectation | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key and stable send identity. |
| `topic` | varchar(200) | Kafka topic to publish to. |
| `message_key` | varchar(200) nullable | Kafka partition key. |
| `payload` | large text | Serialized Kafka value. |
| `headers_json` | large text nullable | JSON-encoded headers persisted for replay and send. |
| `purpose` | varchar(20) | Enum `DISPATCH`, `RETRY`, `DLQ`, `DLQ_REPLAY`, `INTERNAL`. |
| `status` | varchar(20) | Enum `PENDING`, `PROCESSING`, `FAILED`, `SENT`, `DLQED`. |
| `due_at` | timestamp with zone | Earliest publish time. |
| `retry_count` | integer | Outbox publish attempt count. |
| `last_error` | varchar(500) nullable | Last broker or publish error. |
| `created_at` | timestamp with zone | Insert timestamp. |
| `updated_at` | timestamp with zone | Update timestamp. |
| `published_at` | timestamp with zone nullable | Timestamp of successful Kafka acknowledgment. |

### `processed_events`
Used by `JdbcIdempotencyService` from `event-contract`. The essential contract is:

| Column | Purpose |
| --- | --- |
| `event_id` | Stable processed marker key. |
| `consumer_service` | Logical consumer name, usually `spring.application.name`. |

### `shedlock`
Used by `JdbcTemplateLockProvider` for scheduled publisher coordination. The exact column layout is dictated by ShedLock's JDBC provider.

## Operational Rules Derived From Code
- Due rows are claimed with pessimistic locking and immediately transitioned to `PROCESSING`.
- Stale `PROCESSING` rows become claimable again after `notification.outbox.publisher.processing-timeout-ms`.
- Exhausted publish failures are marked `DLQED`; the original outbox row is not deleted.
- DLQ replay is off by default and must be enabled explicitly.
- The module does not purge `kafka_outbox`; retention and cleanup remain the adopter's responsibility.
