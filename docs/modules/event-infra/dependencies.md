# event-infra Dependencies

## Upstream Libraries
`event-infra` depends directly on:

| Dependency | Purpose in code |
| --- | --- |
| `com.platform:event-contract:1.0.0` | Base event envelope types, schema helpers, and JDBC idempotency service. |
| `spring-boot-starter` | Core Spring configuration and lifecycle. |
| `spring-boot-starter-validation` | Validated `@ConfigurationProperties` constraints in `NotificationProperties`. |
| `spring-boot-starter-data-jpa` | JPA entity/repository support for `KafkaOutboxMessage` and lock-backed persistence. |
| `spring-boot-starter-kafka` | `KafkaTemplate`, `@KafkaListener`, topic declarations, and listener error handling. |
| `spring-boot-starter-actuator` | Metrics export integration for Micrometer instruments. |
| `micrometer-tracing` | Tracer abstraction used by tracing utility classes. |
| `resilience4j-spring-boot3` | Shared resilience support for provider-facing executors. |
| `shedlock-spring` | Scheduler locking annotations. |
| `shedlock-provider-jdbc-template` | JDBC lock provider implementation. |

## Runtime Dependencies On The Host Service
This library assumes the consuming service contributes:

| Host capability | Why it is required |
| --- | --- |
| Primary `DataSource` | Needed by `JdbcIdempotencyService`, JPA outbox persistence, and ShedLock. |
| Kafka producer and consumer configuration | Required by `KafkaTemplate` and listener containers. |
| JPA entity scanning for `huynv.eventinfra` | Required so `KafkaOutboxMessage` is mapped. |
| Database tables `kafka_outbox`, `processed_events`, `shedlock` | Required for outbox, consumer idempotency, and distributed scheduling. |
| Business listeners or workers | This library only handles infrastructure delivery, not domain-specific notification composition. |

## Internal Module Coupling
### Outbox
- `KafkaOutboxPublisher` depends on `KafkaOutboxService` and `NotificationProperties`.
- `KafkaOutboxService` depends on `KafkaOutboxRepository` and `ObjectMapper`.
- `KafkaOutboxRepository` expects a mapped JPA entity `KafkaOutboxMessage`.

### Retry And DLQ
- `KafkaConsumerConfig` wires `KafkaOutboxRecoverer` as the common Kafka error handler.
- `KafkaOutboxRecoverer` depends on `RetryBackoffPolicy`, `RetryTopicRouter`, `NotificationMetrics`, and `KafkaOutboxService`.
- `RetryDelayConsumer` depends on `KafkaOutboxService` and reads retry headers defined in `RetryHeaders`.
- `DlqReplayService` depends on `IdempotencyService`, `KafkaOutboxService`, and `NotificationMetrics`.

### Dispatcher
- `NotificationJobPublisher` and `NotificationDispatcherConsumer` both depend on `NotificationProperties` topic mappings and on `KafkaOutboxService` for actual publication.

### Shared Runtime
- `IdempotencyConfig` bridges the `event-contract` JDBC idempotency implementation into Spring.
- `SchedulingConfig` and `ShedLockConfig` provide the scheduling and cluster-safety layer for publisher jobs.
- `TracingConfig` provides a fallback tracer for adopters that do not configure tracing.

## Coupling Notes
- Despite the module name, the config tree is intentionally `notification.*`. That is a real implementation dependency, not just naming style.
- `event-infra` can be shared by more than one service only if those services accept the topic names, metrics names, and outbox semantics or override them explicitly through properties.
