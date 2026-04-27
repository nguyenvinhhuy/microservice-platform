# Event Contract

## 1. Purpose

`event-contract` là shared library định nghĩa contract chuẩn cho event-driven platform:

- envelope `BaseEvent<T>`
- factory tạo event
- metadata/version constants
- typed payload classes cho order/payment/inventory/product
- JSON Schema catalog
- consumer idempotency abstraction + JDBC impl
- schema validation và registry integration abstraction

Đây là source of truth cho shape của event envelope và payload typed được dùng xuyên suốt các service.

## 2. Public Surface

### 2.1 Envelope and Metadata

- `BaseEvent<T>`
- `EventFactory`
- `EventMetadata`
- `EventVersion`
- `UlidGenerator`

### 2.2 Payload Families

- `huynv.event.order.*`
- `huynv.event.payment.*`
- `huynv.event.inventory.*`
- `huynv.event.product.*`

### 2.3 Idempotency

- `IdempotencyService`
- `JdbcIdempotencyService`
- `InMemoryIdempotencyService`

### 2.4 Schema Validation

- `ClasspathSchemaLoader`
- `JsonSchemaValidationService`
- `SchemaRegistryClient`
- `ApicurioRegistryClient`
- `NoopSchemaRegistryClient`

## 3. Canonical Envelope

`BaseEvent<T>` hiện có đúng các field sau:

- `eventId`
- `eventType`
- `source`
- `eventTime`
- `aggregateId`
- `aggregateVersion`
- `dataSchema`
- `traceId`
- `correlationId`
- `causationId`
- `data`

Đây là envelope JSON canonical cho toàn platform.

## 4. Event Creation

`EventFactory` tạo `BaseEvent<T>` theo hai mode:

1. create từ field rời:
   - `eventType`
   - `aggregateId`
   - `aggregateVersion`
   - `dataSchema`
   - `correlationId`
   - `causationId`
   - `data`
2. create từ `EventMetadata`

Behavior quan trọng:

- `eventId` được generate bằng ULID
- `eventTime` lấy từ `Clock`
- `traceId` có thể lấy từ `traceIdSupplier`

## 5. Versioning Rules

Module chỉ expose một constant version:

- `EventVersion.V1 = "v1"`

Schema id thực tế dùng naming dạng:

- `order.created.v1`
- `payment.completed.v1`
- `inventory.stock.updated.v1`

## 6. Payload Catalog

### 6.1 Order

- `OrderCreatedEvent`
- `OrderPaidEvent`
- `OrderFailedEvent`
- `OrderCancelledEvent`

### 6.2 Payment

- `PaymentProcessingEvent`
- `PaymentCompletedEvent`
- `PaymentFailedEvent`

### 6.3 Inventory

- `StockReservedEvent`
- `StockConfirmedEvent`
- `StockReleasedEvent`
- `StockUpdatedEvent`
- `StockReservationFailedEvent`
- `StockItem`

### 6.4 Product

- `ProductUpdatedEvent`
- `ProductPriceUpdatedEvent`

## 7. Schema Catalog

Classpath schemas hiện có:

- `_base-event-envelope.schema`
- `order.created.v1`
- `order.paid.v1`
- `order.failed.v1`
- `order.cancelled.v1`
- `payment.processing.v1`
- `payment.completed.v1`
- `payment.failed.v1`
- `inventory.stock.reserved.v1`
- `inventory.stock.confirmed.v1`
- `inventory.stock.released.v1`
- `inventory.stock.updated.v1`
- `product.updated.v1`
- `product.price.updated.v1`

`ClasspathSchemaLoader` load file theo convention:

- `schemas/<schemaId>.json`

## 8. Schema Validation Flow

`JsonSchemaValidationService.validateAndRegister(schemaId, jsonValue)` làm:

1. load schema từ classpath
2. register schema một lần qua `SchemaRegistryClient`
3. compile schema và cache
4. parse JSON value
5. validate với JSON Schema Draft 2020-12
6. throw `IllegalStateException` nếu có lỗi

## 9. Registry Integration

Module không ép buộc một registry cụ thể.

Các implementation hiện có:

- `NoopSchemaRegistryClient`
- `ApicurioRegistryClient`

`ApicurioRegistryClient` dùng REST API:

- `PUT /apis/registry/v2/groups/{groupId}/artifacts/{schemaId}`
- fallback `POST /apis/registry/v2/groups/{groupId}/artifacts`

## 10. Consumer Idempotency Contract

`IdempotencyService` là abstraction rất nhỏ:

- `alreadyProcessed(eventId)`
- `markProcessed(eventId)`

`JdbcIdempotencyService` giả định table contract:

- `processed_events(event_id, consumer_service, processed_at)`

Behavior:

- duplicate key SQLState `23505` được coi là no-op
- scope dedupe theo cặp `(event_id, consumer_service)`

## 11. Technology and Compatibility

| Area | Value |
| --- | --- |
| Packaging | JAR library |
| Java release | 17 |
| JSON | Jackson 2.19.0 |
| Schema Validation | networknt json-schema-validator 1.5.9 |

## 12. Known Limitations

- module chỉ cung cấp `v1` constant, không có structured version negotiation.
- registry client hiện mới có Apicurio/no-op.
- lỗi validation chỉ trả `IllegalStateException`, không expose chi tiết từng message validation ra API typed riêng.

## 13. Related Artifacts

- [Dependencies](./dependencies.md)
- [Schema catalog](./schemas.md)
