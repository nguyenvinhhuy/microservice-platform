# Order View Service

## 1. Purpose

`order-view-service` là read-side projection service cho order lifecycle. Nó consume event từ `order-service`, `payment-service`, và `inventory-service`, materialize dữ liệu vào bảng `order_view`, rồi expose query APIs tenant-scoped để phục vụ read path.

## 2. Key Functions

- Consume `order.events`, `payment.events`, `inventory.events`.
- Materialize projection vào `order_view`.
- Enforce consumer idempotency bằng `processed_events`.
- Expose query APIs:
  - `GET /orders`
  - `GET /orders/{id}`

## 3. Read Model

Projection lưu trong bảng `order_view` với khóa chính tổng hợp:

- `tenant_id`
- `order_id`

Trường hiện có:

- `user_id`
- `status`
- `payment_status`
- `stock_status`
- `total_price`
- `created_at`
- `updated_at`

## 4. Event Consumption

### 4.1 Order Events

`OrderViewEventConsumer.onOrderEvent()` consume `${orderview.kafka.order-topic}` mặc định `order.events`.

Event type được xử lý:

- `order.created`
- `order.paid`
- `order.failed`

Mapping:

- `order.created`
  - tạo hoặc upsert row với `userId`, `status`, `totalAmount`, `createdAt`
- `order.paid`
  - set `status = PAYMENT_COMPLETED`
- `order.failed`
  - set `status = ORDER_FAILED`

### 4.2 Payment Events

`OrderViewEventConsumer.onPaymentEvent()` consume `${orderview.kafka.payment-topic}` mặc định `payment.events`.

Event type được xử lý:

- `payment.completed`
- `payment.failed`

Mapping:

- `payment.completed` -> `paymentStatus = PAYMENT_COMPLETED`
- `payment.failed` -> `paymentStatus = PAYMENT_FAILED`

### 4.3 Inventory Events

`OrderViewEventConsumer.onInventoryEvent()` consume `${orderview.kafka.inventory-topic}` mặc định `inventory.events`.

Event type được xử lý:

- `inventory.stock.reserved`
- `inventory.stock.confirmed`
- `inventory.stock.released`

Mapping hiện tại:

- `inventory.stock.reserved` -> `stockStatus = STOCK_RESERVED`
- `inventory.stock.confirmed` -> `stockStatus = STOCK_RESERVED`
- `inventory.stock.released` -> `stockStatus = STOCK_RELEASED`

Lưu ý: code hiện không có trạng thái `STOCK_CONFIRMED`; event confirm vẫn map về `STOCK_RESERVED`.

## 5. Projection Write Semantics

`OrderViewProjectionService` cho phép partial upsert:

- `upsertCreated()` tạo row base từ `order.created`
- `updateOrderStatus()`, `updatePaymentStatus()`, `updateStockStatus()` có thể tạo row mới nếu row chưa tồn tại

Điều đó có nghĩa là projection có thể xuất hiện ở trạng thái partial nếu event đến lệch thứ tự.

Ví dụ:

- payment event đến trước `order.created`
- inventory event đến trước `order.created`

Trong các case này row vẫn có thể được tạo với một phần trường null.

## 6. Query APIs

### 6.1 List Orders

`GET /orders`

Headers:

- bắt buộc `X-Tenant-Id`
- tùy chọn `X-User-Id`

Query params:

- `page` mặc định `0`
- `size` mặc định `50`

Behavior:

- nếu có `X-User-Id` thì query theo `(tenantId, userId)`
- nếu không có thì query toàn bộ order của tenant

### 6.2 Get Order By Id

`GET /orders/{id}`

Headers:

- bắt buộc `X-Tenant-Id`

Path:

- `id` là `orderId` kiểu UUID

Hiện tại controller dùng `orElseThrow()` mặc định, nên không có explicit 404 mapping trong module.

## 7. Idempotency

Consumer idempotency dùng `JdbcIdempotencyService` qua `IdempotencyConfig`.

- table: `processed_events`
- key: `(event_id, consumer_service)`
- flow:
  - check `alreadyProcessed(eventId)`
  - apply projection
  - `markProcessed(eventId)`

## 8. Consistency Model

`order-view-service` là eventually consistent query model.

- projection phụ thuộc thứ tự event delivery
- row có thể partial
- không có rebuild endpoint
- không có explicit compensating cleanup hay delete handling

## 9. Multi-Tenancy and Security Notes

- primary key projection chứa `tenantId`
- list query filter theo tenant, và tùy chọn user
- single item query dùng composite id `(tenantId, orderId)`
- module hiện không có Spring Security config riêng
- service hiện tin trực tiếp `X-Tenant-Id` và `X-User-Id` headers

## 10. Error Semantics

Service hiện không có `@RestControllerAdvice`.

- parse lỗi event -> `IllegalStateException`
- query miss ở `GET /orders/{id}` -> unchecked exception mặc định của Spring
- HTTP contract lỗi chưa được chuẩn hóa riêng

## 11. Tech Stack

| Area | Value |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Build | Maven |
| Database | PostgreSQL |
| Messaging | Kafka |
| Migration | Flyway |
| Telemetry | Micrometer + OpenTelemetry |

## 12. Known Limitations

- `inventory.stock.confirmed` vẫn map thành `STOCK_RESERVED`.
- `order.paid` mutate `status`, trong khi payment event mutate `paymentStatus`; hai field này không phải canonical state machine thống nhất.
- query single item chưa map 404 rõ ràng.
- không có auth layer riêng; tenant isolation dựa trên caller gửi đúng header.

## 13. Related Artifacts

- [API spec](./api/order-view.yaml)
- [Event spec](./events/order-view-events.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
