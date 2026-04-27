# Audit Log Service

## 1. Purpose

`audit-log-service` là immutable event sink để lưu dấu vết audit của các domain event quan trọng. Nó consume event từ các topic business, persist toàn bộ raw envelope vào bảng `audit_log`, rồi expose query API tenant-scoped cho audit/readback.

## 2. Key Functions

- Consume `order.events`, `payment.events`, `inventory.events`, `product.events`.
- Persist immutable audit row cho mỗi `eventId`.
- Enforce consumer idempotency bằng `processed_events`.
- Expose query APIs:
  - `GET /api/audit-logs`
  - `GET /api/audit-logs/{id}`
  - `GET /api/audit-logs/user/{userId}`
  - `GET /api/audit-logs/search`

## 3. Audit Storage Model

Bảng chính: `audit_log`

Field chính:

- `id`
- `event_id`
- `event_type`
- `source`
- `tenant_id`
- `user_id`
- `aggregate_id`
- `aggregate_type`
- `correlation_id`
- `causation_id`
- `raw_payload`
- `received_at`

Invariant hiện có:

- `event_id` unique trong bảng
- row là immutable theo intent; service chỉ insert, không có update API

## 4. Inbound Event Scope

`AuditEventConsumer` consume 4 topic:

- `${auditlog.kafka.order-topic}` mặc định `order.events`
- `${auditlog.kafka.payment-topic}` mặc định `payment.events`
- `${auditlog.kafka.inventory-topic}` mặc định `inventory.events`
- `${auditlog.kafka.product-topic}` mặc định `product.events`

Consumer được bật bởi:

- `auditlog.kafka.consumer-enabled=true` mặc định

## 5. Event Ingestion Flow

Flow chung:

1. nhận Kafka record
2. nếu payload rỗng thì warn và acknowledge luôn
3. parse `BaseEvent<JsonNode>`
4. nếu không có `eventId` thì warn và acknowledge luôn
5. check `processed_events`
6. extract `tenantId` và `userId` từ `data`
7. persist audit row
8. mark processed
9. manual acknowledge

Nếu bất kỳ bước parse/persist fail:

- log error
- rethrow `IllegalStateException`
- offset không được ack ở path lỗi

## 6. Idempotency

Service dùng `JdbcIdempotencyService`.

- table: `processed_events`
- key: `(event_id, consumer_service)`
- mục tiêu: tránh lưu duplicate audit row khi Kafka redelivery xảy ra

Ngoài ra bảng `audit_log` còn có unique index theo `event_id`.

## 7. Aggregate Type Derivation

`AuditLogService` derive `aggregateType` bằng prefix của `eventType`.

Ví dụ:

- `order.created` -> `order`
- `payment.failed` -> `payment`

Nếu `eventType` không có dấu `.` thì aggregate type giữ nguyên `eventType`.

## 8. Query APIs

### 8.1 List Audit Logs

`GET /api/audit-logs`

Header:

- bắt buộc `X-Tenant-Id`

Filter:

- `eventType` optional
- `aggregateId` optional
- `page`, `size`

Behavior:

- nếu có `eventType` thì filter theo `(tenantId, eventType)`
- nếu có `aggregateId` thì filter theo `(tenantId, aggregateId)`
- nếu không thì list toàn bộ tenant
- sort theo `receivedAt DESC`

### 8.2 Get Audit Log By Id

`GET /api/audit-logs/{id}`

Header:

- bắt buộc `X-Tenant-Id`

Behavior:

- load by surrogate `id`
- chỉ trả `200` nếu row có cùng `tenantId`
- khác tenant hoặc không tồn tại -> `404`

### 8.3 List Audit Logs By User

`GET /api/audit-logs/user/{userId}`

Header:

- bắt buộc `X-Tenant-Id`

Behavior:

- filter `(tenantId, userId)`
- sort `receivedAt DESC`

### 8.4 Search

`GET /api/audit-logs/search`

Hiện tại chỉ delegate về `listAuditLogs()`. Nó không có full-text search riêng; chỉ là alias endpoint cho cùng filter `eventType` / `aggregateId`.

## 9. Multi-Tenancy and Security Notes

- read API hoàn toàn tenant-scoped qua `X-Tenant-Id`
- `getById` còn verify tenant ownership trước khi trả dữ liệu
- service module hiện không có Spring Security config riêng
- write path (Kafka consumer) không reject event thiếu tenantId; row vẫn có thể được lưu với `tenantId = null` nếu event hợp lệ nhưng payload không có tenant

## 10. Error Semantics

- `GET /api/audit-logs/{id}` có explicit `404` qua `ResponseEntity.notFound()`
- các query list/search còn lại không có custom exception mapping
- Kafka parse/persist lỗi -> `IllegalStateException`
- event không có `eventId` hoặc payload rỗng -> skip + ack, không DLQ ở local logic

## 11. Kafka Consumer Semantics

- `enable-auto-commit=false`
- listener `ack-mode=manual_immediate`
- consumer chỉ ack sau khi persist + mark processed thành công

Điều này làm behavior gần với at-least-once + idempotent persistence.

## 12. Tech Stack

| Area | Value |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Build | Maven |
| Database | PostgreSQL |
| Messaging | Kafka |
| Migration | Flyway |
| Telemetry | Micrometer + OpenTelemetry |

## 13. Known Limitations

- chỉ audit 4 topic cố định, không consume toàn bộ platform topic dù mô tả module hơi rộng.
- event không có `eventId` sẽ bị ack-skip nên không có bản ghi audit fallback.
- không có retention/purge job trong module hiện tại.
- `search` chỉ là alias endpoint, không có search semantics sâu hơn.
- không có auth layer riêng trong module.

## 14. Related Artifacts

- [API spec](./api/audit-log.yaml)
- [Event spec](./events/audit-log-events.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
