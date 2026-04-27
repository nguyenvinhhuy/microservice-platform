# Product View Service

## 1. Purpose

`product-view-service` là read-side projection service cho product catalogue. Nó consume event từ `product-service` và `inventory-service`, materialize dữ liệu vào bảng `product_view`, rồi expose query API đơn giản để đọc danh sách/single product theo tenant.

## 2. Key Functions

- Consume `product.events` và apply update vào projection.
- Consume `inventory.events` và cập nhật stock snapshot vào projection.
- Enforce consumer idempotency bằng `processed_events`.
- Expose query APIs:
  - `GET /products`
  - `GET /products/{id}`

## 3. Read Model

Projection được lưu trong bảng `product_view` với khóa chính tổng hợp:

- `tenant_id`
- `product_id`

Trường hiện có:

- `name`
- `price`
- `stock`
- `status`
- `updated_at`

## 4. Event Consumption

### 4.1 Product Events

`ProductViewEventConsumer.onProductEvent()` consume `${productview.kafka.product-topic}` mặc định `product.events`.

Event type được xử lý:

- `product.updated`
- `product.price.updated`

Mapping hiện tại:

- `product.updated`
  - đọc `tenantId`, `productId`, `name`, `price`
  - ghi `status = ACTIVE`
- `product.price.updated`
  - chỉ cập nhật `price`

Service hiện không đọc status thật từ payload sản phẩm. `product.updated` luôn ép `status` về `ACTIVE`.

### 4.2 Inventory Events

`ProductViewEventConsumer.onInventoryEvent()` consume `${productview.kafka.inventory-topic}` mặc định `inventory.events`.

Chỉ event type sau được xử lý:

- `inventory.stock.updated`

Mapping hiện tại:

- `availableStock -> stock`
- `availableStock > 0 -> status = IN_STOCK`
- `availableStock <= 0 -> status = OUT_OF_STOCK`

Nghĩa là trạng thái projection có thể bị event inventory overwrite sau khi event product đã set `ACTIVE`.

## 5. Idempotency

Consumer dùng `JdbcIdempotencyService` qua `IdempotencyConfig`.

- table: `processed_events`
- key: `(event_id, consumer_service)`
- flow:
  - nếu `alreadyProcessed(eventId)` thì skip
  - nếu chưa thì apply projection
  - sau đó `markProcessed(eventId)`

## 6. Query APIs

### 6.1 List Products

`GET /products`

Headers:

- bắt buộc `X-Tenant-Id`

Query params:

- `page` mặc định `0`
- `size` mặc định `50`

Result:

- `Page<ProductViewResponse>`

### 6.2 Get Product By Id

`GET /products/{id}`

Headers:

- bắt buộc `X-Tenant-Id`

Path params:

- `id` là `productId`

Hiện tại nếu không tìm thấy row, controller dùng `orElseThrow()` mặc định nên response thực tế sẽ là lỗi server mặc định của Spring nếu không có exception mapping riêng.

## 7. Consistency Model

`product-view-service` là eventually consistent read model.

- dữ liệu phụ thuộc thứ tự và độ trễ event delivery
- không có rebuild endpoint
- không có delete event handling trong code hiện tại
- partial projections là hợp lệ: inventory event có thể tạo row trước product event, và ngược lại

## 8. Multi-Tenancy and Security Notes

- projection key chứa `tenantId`
- query list dùng `findByIdTenantId(...)`
- query single product dùng composite id `(tenantId, productId)`
- service hiện tin trực tiếp header `X-Tenant-Id`
- module không có Spring Security config riêng

## 9. Error Semantics

Service hiện không có `@RestControllerAdvice`.

- parse lỗi event -> `IllegalStateException`
- query miss ở `GET /products/{id}` -> unchecked exception mặc định
- HTTP error contract vì vậy vẫn là Spring default, chưa được chuẩn hóa

## 10. Tech Stack

| Area | Value |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Build | Maven |
| Database | PostgreSQL |
| Messaging | Kafka |
| Migration | Flyway |
| Telemetry | Micrometer + OpenTelemetry |

## 11. Known Limitations

- `product.updated` hard-code `status = ACTIVE`.
- inventory update có thể overwrite status thành `IN_STOCK` hoặc `OUT_OF_STOCK`.
- không có delete/deactivation handling.
- không có explicit 404 mapping cho query single item.
- không có auth layer riêng; tenant isolation phụ thuộc caller gửi đúng `X-Tenant-Id`.

## 12. Related Artifacts

- [API spec](./api/product-view.yaml)
- [Event spec](./events/product-view-events.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
