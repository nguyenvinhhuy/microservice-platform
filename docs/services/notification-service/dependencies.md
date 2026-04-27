# Notification Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | `BaseEvent`, idempotency interface, shared event model. |
| `event-infra:1.0.0` | Kafka outbox, notification dispatcher jobs, shared metrics/config/utility components. |
| Spring Web | REST APIs for history and preferences. |
| Spring Data JPA | Persistence for history, preferences, processed notifications. |
| Spring Kafka | Event consumption and worker topic processing. |
| Spring Data Redis | Contact cache when enabled. |
| Spring Security OAuth2 Resource Server | JWT validation and authenticated API access. |
| Thymeleaf | Notification template rendering. |
| Spring Mail | Email integration support. |
| PostgreSQL | Primary transactional store. |
| Flyway | Schema migration management. |
| Micrometer / OpenTelemetry | Metrics and tracing. |
| ShedLock | Scheduler locking for shared infra jobs. |

## Inbound Interfaces

### REST

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/notifications` | JWT required | List recent notification history for current tenant/user. |
| `GET` | `/notification-preferences` | JWT required | List current tenant/user channel preferences. |
| `PUT` | `/notification-preferences` | JWT required | Upsert current tenant/user channel preferences. |

### Kafka

| Topic | Consumer | Purpose |
| --- | --- | --- |
| `order.events` | `NotificationEventConsumer` | Ingest upstream order events and republish into internal stream. |
| `payment.events` | `NotificationEventConsumer` | Ingest upstream payment events and republish into internal stream. |
| `notification.events` | `NotificationInternalEventConsumer` | Expand normalized internal events into per-channel jobs. |
| `notification.email` | `EmailWorker` | Execute email delivery jobs. |
| `notification.sms` | `SmsWorker` | Execute SMS delivery jobs. |
| `notification.push` | `PushWorker` | Execute push delivery jobs. |

## Outbound Interfaces

### Kafka

| Topic | Publisher | Purpose |
| --- | --- | --- |
| `notification.events` | `KafkaOutboxService` via `NotificationEventConsumer` | Internal normalized event stream. |
| `notification.high` / `notification.normal` / `notification.low` | `NotificationJobPublisher` via `NotificationIngestionService` | Priority dispatch topics managed by shared infra. |
| `notification.email` / `notification.sms` / `notification.push` | Dispatcher infra | Channel-specific delivery work queues. |

### HTTP/Internal Service Calls

| Dependency | Purpose |
| --- | --- |
| `order-view-service` | Resolve `orderId` to `userId` when events omit `userId`. |
| `user-service` | Resolve recipient contact data. |

### External Providers

| Channel | Provider abstraction |
| --- | --- |
| Email | `EmailProvider` |
| SMS | `SmsProvider` |
| Push | `PushProvider` |

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `notification_history` | Audit and user-facing history of attempts/outcomes. |
| `notification_preferences` | Tenant-aware per-user channel preferences. |
| `processed_notifications` | Per-channel idempotency markers. |
| `processed_events` | Legacy consumer idempotency markers. |
| `processed_events_v2` | Tenant-aware consumer idempotency table introduced by migration V5. |
| `processed_notifications_v2` | Tenant-aware per-channel idempotency table introduced by migration V5. |
| `kafka_outbox` | Shared outbox table used by `event-infra`. |
| `shedlock` | Scheduler lock table. |

## Configuration Surface

Key groups:

- `notification.kafka.*`
- `notification.dispatch.*`
- `notification.dispatcher.*`
- `notification.workers.*`
- `notification.retry.*`
- `notification.outbox.publisher.*`
- `notification.integrations.*`
- `notification.redis-cache.*`
- `notification.rate-limits.*`
- `notification.templates.*`
- `notification.channels.*`
- `notification.email.*`
- `notification.sms.*`
- `notification.push.*`

## Notable Constraints

- Only a narrow set of event types is converted into notifications.
- REST APIs are tenant/user scoped through JWT claims, not request headers.
- Channel worker idempotency is tenant-scoped by a derived key `tenantId:eventId`.
- Some outbox, dispatcher, and idempotency behavior is implemented in `event-infra`, not directly in this module.
