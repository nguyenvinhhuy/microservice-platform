# Product View Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `event-contract:1.0.0` | `BaseEvent` envelope and JDBC idempotency support. |
| Spring Web | Query REST API. |
| Spring Data JPA | Projection persistence. |
| Spring Kafka | Consume product and inventory events. |
| PostgreSQL | Read model storage. |
| Flyway | Schema migration management. |
| Micrometer / OpenTelemetry | Metrics and tracing. |

## Inbound Interfaces

### REST

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/products` | List product projection rows for one tenant. |
| `GET` | `/products/{id}` | Get one product projection row for one tenant. |

### Kafka

| Topic | Event Types Used | Purpose |
| --- | --- | --- |
| `product.events` | `product.updated`, `product.price.updated` | Update name/price/status fields. |
| `inventory.events` | `inventory.stock.updated` | Update stock/status fields. |

## Persistence Dependencies

| Table | Purpose |
| --- | --- |
| `product_view` | Denormalized read model for product queries. |
| `processed_events` | Consumer idempotency markers. |

## Configuration Surface

| Key | Default | Purpose |
| --- | --- | --- |
| `productview.kafka.product-topic` | `product.events` | Product event topic. |
| `productview.kafka.inventory-topic` | `inventory.events` | Inventory event topic. |
| `productview.kafka.group-id` | `product-view-service` | Consumer group id. |

## Notable Constraints

- Query API requires `X-Tenant-Id`.
- No dedicated exception mapping layer is present.
- Projection status is derived inconsistently from different event sources.
