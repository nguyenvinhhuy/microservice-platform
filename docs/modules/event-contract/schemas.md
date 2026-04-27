# Event Contract Schema Catalog

## Envelope

- `_base-event-envelope.schema`

## Order Schemas

- `order.created.v1`
- `order.paid.v1`
- `order.failed.v1`
- `order.cancelled.v1`

## Payment Schemas

- `payment.processing.v1`
- `payment.completed.v1`
- `payment.failed.v1`

## Inventory Schemas

- `inventory.stock.reserved.v1`
- `inventory.stock.confirmed.v1`
- `inventory.stock.released.v1`
- `inventory.stock.updated.v1`

## Product Schemas

- `product.updated.v1`
- `product.price.updated.v1`

## Naming Convention

Schema ids are used directly by `ClasspathSchemaLoader` and `JsonSchemaValidationService`:

- file path = `schemas/<schemaId>.json`
- schema id includes explicit version suffix
- event publishers typically set `BaseEvent.dataSchema = <schemaId>`
