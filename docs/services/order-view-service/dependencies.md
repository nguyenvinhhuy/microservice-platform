# Order View Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | `BaseEvent` envelope and JDBC idempotency support. |
| Spring Web | Query REST API. |
| Spring Data JPA | Projection persistence. |
| Spring Kafka | Consume order, payment, and inventory events. |
| PostgreSQL | Read model storage. |
| Flyway | Schema migration management. |
| Micrometer / OpenTelemetry | Metrics and tracing. |

## Inbound Interfaces

### REST

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/orders` | List order projection rows for one tenant, optionally one user. |
| `GET` | `/orders/{id}` | Get one order projection row for one tenant. |

### Kafka

| Topic | Event Types Used | Purpose |
| --- | --- | --- |
| `order.events` | `order.created`, `order.paid`, `order.failed` | Update order core fields/status. |
| `payment.events` | `payment.completed`, `payment.failed` | Update payment status. |
| `inventory.events` | `inventory.stock.reserved`, `inventory.stock.confirmed`, `inventory.stock.released` | Update stock status. |

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `order_view` | Denormalized read model for order queries. |
| `processed_events` | Consumer idempotency markers. |

## Configuration Surface

| Key | Default | Purpose |
| --- | --- | --- |
| `orderview.kafka.order-topic` | `order.events` | Order event topic. |
| `orderview.kafka.payment-topic` | `payment.events` | Payment event topic. |
| `orderview.kafka.inventory-topic` | `inventory.events` | Inventory event topic. |
| `orderview.kafka.group-id` | `order-view-service` | Consumer group id. |

## Notable Constraints

- Query API requires `X-Tenant-Id`.
- Optional `X-User-Id` changes list query scope.
- Projection rows can be created partially from non-order events.
