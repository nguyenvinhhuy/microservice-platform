# Event Contract Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| `jackson-annotations` | Envelope/payload JSON annotations. |
| `jackson-databind` | JSON parsing for schema validation and envelope processing. |
| `json-schema-validator` | JSON Schema Draft 2020-12 validation. |

## Public Packages

| Package | Purpose |
| --- | --- |
| `huynv.event` | Envelope, metadata, factory, event constants. |
| `huynv.event.order` | Order payload records. |
| `huynv.event.payment` | Payment payload records. |
| `huynv.event.inventory` | Inventory payload records. |
| `huynv.event.product` | Product payload records. |
| `huynv.event.idempotency` | Consumer idempotency abstractions and implementations. |
| `huynv.event.schema` | Schema loading, validation, and registry abstractions. |

## Storage Contract Assumptions

`JdbcIdempotencyService` expects:

- table `processed_events`
- columns:
  - `event_id`
  - `consumer_service`
  - `processed_at`
- duplicate key semantics on `(event_id, consumer_service)`

## Resource Contract

Classpath resource convention:

- `schemas/<schemaId>.json`

Examples:

- `schemas/order.created.v1.json`
- `schemas/payment.completed.v1.json`
- `schemas/_base-event-envelope.schema.json`
