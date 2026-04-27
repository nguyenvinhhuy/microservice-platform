# Audit Log Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | `BaseEvent` envelope and JDBC idempotency support. |
| Spring Web | Query REST API. |
| Spring Data JPA | Audit log persistence and querying. |
| Spring Kafka | Consume business event topics. |
| PostgreSQL | Immutable audit storage. |
| Flyway | Schema migration management. |
| Micrometer / OpenTelemetry | Metrics and tracing. |

## Inbound Interfaces

### REST

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/audit-logs` | List tenant audit logs with optional filters. |
| `GET` | `/api/audit-logs/{id}` | Get one tenant audit log row. |
| `GET` | `/api/audit-logs/user/{userId}` | List tenant audit logs for one user. |
| `GET` | `/api/audit-logs/search` | Alias search endpoint using same filters as list. |

### Kafka

| Topic | Consumer | Purpose |
| --- | --- | --- |
| `order.events` | `AuditEventConsumer` | Persist order event audit trail. |
| `payment.events` | `AuditEventConsumer` | Persist payment event audit trail. |
| `inventory.events` | `AuditEventConsumer` | Persist inventory event audit trail. |
| `product.events` | `AuditEventConsumer` | Persist product event audit trail. |

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `audit_log` | Immutable stored event audit entries. |
| `processed_events` | Consumer idempotency markers. |

## Configuration Surface

| Key | Default | Purpose |
| --- | --- | --- |
| `auditlog.kafka.order-topic` | `order.events` | Order event topic. |
| `auditlog.kafka.payment-topic` | `payment.events` | Payment event topic. |
| `auditlog.kafka.inventory-topic` | `inventory.events` | Inventory event topic. |
| `auditlog.kafka.product-topic` | `product.events` | Product event topic. |
| `auditlog.kafka.group-id` | `audit-log-service` | Consumer group id. |
| `auditlog.kafka.consumer-enabled` | `true` | Enable audit Kafka consumers. |

## Notable Constraints

- API reads are tenant-scoped by `X-Tenant-Id`.
- Consumer manually acknowledges only after successful persistence.
- Empty payloads and events without `eventId` are skipped and acknowledged.
