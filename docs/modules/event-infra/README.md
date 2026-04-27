# event-infra

## Purpose
`event-infra` is a shared infrastructure library for Kafka-based event delivery. In the current codebase it is tuned to the notification pipeline rather than being a fully generic messaging platform: it provides database-backed outbox publishing, topic-tier retry routing, DLQ replay, dispatcher fan-out, ShedLock-backed schedulers, idempotent Kafka consumer support, and shared tracing/metrics helpers.

## Scope
This module is consumed as a Spring Boot library. It does not expose HTTP APIs and it does not own database migrations or standalone runtime entrypoints. Services that adopt it must provide the required tables, Kafka broker configuration, and any service-specific business handlers on top of these primitives.

## Key Building Blocks
### Transactional Kafka Outbox
- `KafkaOutboxMessage` persists records in `kafka_outbox` with `purpose`, `status`, `dueAt`, `retryCount`, `lastError`, and `publishedAt`.
- `KafkaOutboxService` inserts outbox rows, claims due rows with pessimistic locking, reclaims stale `PROCESSING` rows after a lease timeout, marks successful sends as `SENT`, and exhausts repeated failures into `DLQED`.
- `KafkaOutboxPublisher` runs on a fixed delay, is guarded by ShedLock name `notification-service-kafka-outbox-publisher`, enforces `maxInflight` backpressure, and publishes through `KafkaTemplate<String, String>`.
- Publish failure handling is bounded: retries are rescheduled through `markFailed`, and once the configured attempt budget is exhausted the service also enqueues a DLQ message unless the failed row was already targeting the DLQ.

### Retry And DLQ Pipeline
- `KafkaConsumerConfig` installs a shared `DefaultErrorHandler` with `KafkaOutboxRecoverer` so failed consumer records are persisted to the outbox instead of being retried inline.
- `KafkaOutboxRecoverer` classifies `InvalidEventPayloadException`, `NonRetryableNotificationException`, and `IllegalArgumentException` as non-retryable. Other failures are routed through the tiered retry topics until the retry budget is exhausted.
- Retry tiers are topic-based, not sleep-based. The current topics are `notification.retry.1m`, `notification.retry.5m`, and `notification.retry.30m`.
- `RetryDelayConsumer` consumes those retry topics and republishes records back to their retry target through the outbox when the `retry_due_at_ms` header is reached.
- `DlqReplayService` is opt-in via `notification.dlq-replay.enabled` or `notification.dlq.replay.enabled`. It replays DLQ records through the outbox and uses `IdempotencyService` to prevent replaying the same `(topic, partition, offset)` record twice.

### Dispatcher Fan-Out
- `NotificationJobPublisher` serializes `NotificationJob` records and publishes them into priority topics using outbox purpose `DISPATCH`.
- `NotificationDispatcherConsumer` consumes `notification.high`, `notification.normal`, and `notification.low`, then forwards jobs to channel-specific worker topics `notification.email`, `notification.sms`, and `notification.push`.
- Partition keys prefer `tenantId:orderId`, then `tenantId:userId`, then `tenantId:eventId` to keep related jobs co-located when possible.
- Dispatcher listeners are gated by `notification.dispatcher.enabled` and `notification.dispatch.enabled`.

### Shared Config And Runtime Guards
- `NotificationProperties` is the central configuration surface. It defines Kafka topic names, listener concurrency, retry budgets, outbox publisher settings, dispatcher weights, worker pool sizes, rate limits, DLQ replay controls, and synthetic contact flags.
- `IdempotencyConfig` exports a JDBC-backed `IdempotencyService` using `JdbcIdempotencyService` from `event-contract`. The default consumer service name comes from `spring.application.name`.
- `SchedulingConfig` enables scheduling and applies a default ShedLock lease of `PT30S`.
- `ShedLockConfig` stores distributed locks in the service database through `JdbcTemplateLockProvider`.
- `TracingConfig` supplies `Tracer.NOOP` when Micrometer tracing is absent so adopters do not fail startup.

## Runtime Topics
Current default topics declared by the module:

| Role | Default topic |
| --- | --- |
| Internal events buffer | `notification.events` |
| Retry tier 1 | `notification.retry.1m` |
| Retry tier 2 | `notification.retry.5m` |
| Retry tier 3 | `notification.retry.30m` |
| Dead-letter topic | `notification.dlq` |
| Priority high | `notification.high` |
| Priority normal | `notification.normal` |
| Priority low | `notification.low` |
| Worker email | `notification.email` |
| Worker SMS | `notification.sms` |
| Worker push | `notification.push` |

`KafkaConsumerConfig` creates these topics as `NewTopic` beans with fixed partition counts. Adopters should treat those defaults as code-level behavior unless they override properties explicitly.

## Persistence Contract
The module expects the consuming service to provide:

| Table | Used by | Required behavior |
| --- | --- | --- |
| `kafka_outbox` | `KafkaOutboxService`, `KafkaOutboxPublisher` | Stores reliable publish records and retry state. |
| `processed_events` | `JdbcIdempotencyService`, `DlqReplayService`, consumer handlers | Stores `(event_id, consumer_service)` markers for exactly-once side effects per consumer. |
| `shedlock` | `ShedLockConfig` | Coordinates scheduled jobs across service replicas. |

This module does not include Flyway/Liquibase migrations or SQL resources. Schema ownership remains with the consuming service.

## Metrics And Observability
- `NotificationMetrics` emits counters and timers for send/fail/skip outcomes, retry scheduling, DLQ routing, replay activity, provider timeouts, rate limit rejections, and worker queue backpressure.
- `KafkaOutboxPublisher` also publishes gauges by outbox purpose: dispatch, retry, DLQ, DLQ replay, and internal backlog depth.
- `MdcUtil`, `TraceHeaderUtil`, and `TracingUtil` are shared helpers for propagating `tenantId`, `orderId`, `eventId`, `traceId`, and correlation metadata across Kafka boundaries.

## Technology
- Java 25
- Spring Boot 4.0.6
- Spring Kafka
- Spring Data JPA
- Micrometer tracing and metrics
- Resilience4j
- ShedLock JDBC provider
- Dependency on `event-contract:1.0.0`

## Known Boundaries
- The property model and topic defaults are notification-centric. Reusing this module in another service without overrides will inherit `notification.*` naming and semantics.
- Retry backoff is bounded by the configured retry topics and outbox attempt budget; it is not an unbounded exponential retry system.
- Topic creation, outbox entity shape, and metrics names are implementation contract in this repository and should be kept backward compatible for adopters.
