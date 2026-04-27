# Notification Service

## 1. Purpose

`notification-service` chuyển các domain event từ `order-service` và `payment-service` thành notification thực tế cho người dùng. Service này không chỉ là một consumer đơn giản; nó là một pipeline nhiều tầng gồm:

1. consume event nghiệp vụ upstream
2. republish vào internal notification stream qua outbox
3. resolve recipient và contact
4. fan-out thành job theo channel
5. worker theo channel gửi ra provider
6. ghi notification history để audit và hiển thị cho user

Ngoài flow event-driven, service còn có REST API để user xem lịch sử notification và cập nhật preference theo tenant/user đã xác thực bằng JWT.

## 2. Key Functions

- Consume `order.events` và `payment.events`.
- Parse `BaseEvent<JsonNode>` và chỉ map các event type liên quan đến notification.
- Republish event hợp lệ sang internal topic `notification.events` qua `KafkaOutboxService` từ `event-infra`.
- Ingest internal event, resolve recipient, apply notification preferences, rồi publish `NotificationJob` sang topic theo priority/channel.
- Xử lý gửi Email, SMS, Push qua worker riêng với rate limit, provider timeout và per-channel idempotency.
- Persist `notification_history` cho outcome `SENT`, `FAILED`, `SKIPPED`.
- Expose authenticated APIs:
  - `GET /notifications`
  - `GET /notification-preferences`
  - `PUT /notification-preferences`

## 3. Supported Event Types

`NotificationProcessingService` hiện chỉ tạo `NotificationIntent` cho các event type sau:

- `order.created`
- `order.cancelled`
- `payment.completed`
- `payment.succeeded`
- `payment.failed`

Các event type khác được parse xong nhưng bị bỏ qua, không tạo notification.

## 4. Event Processing Topology

### 4.1 Upstream Consumer Layer

`NotificationEventConsumer` consume:

- `${notification.kafka.order-topic}` mặc định `order.events`
- `${notification.kafka.payment-topic}` mặc định `payment.events`

Flow:

1. parse envelope
2. validate field bắt buộc
3. lấy `tenantId` từ `data`, không tin Kafka header
4. build routing key tenant-aware
5. enqueue raw event sang `${notification.kafka.events-topic}` mặc định `notification.events`
6. manual acknowledge sau khi enqueue thành công

Layer này không gửi notification trực tiếp. Nó chỉ republish sang internal stream qua outbox library `KafkaOutboxService`.

### 4.2 Internal Event Layer

`NotificationInternalEventConsumer` consume `notification.events`.

Flow:

1. parse và validate lại envelope
2. dedupe bằng `IdempotencyService`
3. gọi `NotificationIngestionService.ingest(rawJson)`
4. mark processed khi ingest thành công
5. manual acknowledge

Điểm quan trọng:

- service dedupe internal event theo `eventId`
- logic idempotency implementation được inject từ shared infra/library, không nằm trọn trong module này
- migration của module có cả bảng legacy `processed_events` và bảng tenant-aware `processed_events_v2`

### 4.3 Notification Ingestion Layer

`NotificationIngestionService` thực hiện:

1. map `BaseEvent` thành `NotificationIntent`
2. resolve `userId`
   - nếu event đã có `userId` thì dùng luôn
   - nếu không có thì lookup `order-view-service` qua `orderId`
3. resolve contact qua `UserContactResolver`
4. đọc enabled channels từ `NotificationPreferenceService`
5. xác định priority
   - `PAYMENT_FAILED` -> `HIGH`
   - `ORDER_CANCELLED` -> `HIGH`
   - `PAYMENT_SUCCEEDED` -> `NORMAL`
   - `ORDER_CREATED` -> `NORMAL`
6. tạo `NotificationJob` theo channel đủ dữ liệu
7. publish job qua `NotificationJobPublisher` từ `event-infra`

Nếu không resolve được recipient thì service ném `RetryableDependencyException`.

Nếu channel thiếu contact data phù hợp:

- job không được tạo
- history được ghi với `SKIPPED`

## 5. Channel Workers

Service có ba worker:

- `EmailWorker`
- `SmsWorker`
- `PushWorker`

Mỗi worker:

- consume topic channel riêng
- deserialize `NotificationJob`
- áp rate limit theo tenant/channel
- render template bằng Thymeleaf
- gọi provider trong executor riêng với timeout cứng
- persist `ProcessedNotification` để tránh duplicate send theo channel
- ghi `notification_history`
- acknowledge khi send thành công hoặc khi fail non-retryable đã được ghi nhận

Per-channel idempotency dùng `processed_notifications` với key:

- `eventId`
- `channel`

Trong worker, `eventId` được transform thành tenant-scoped composite key bằng `IdempotencyKeyUtil.tenantScopedEventId(tenantId, eventId)` để tránh collision cross-tenant.

## 6. REST APIs

### 6.1 Notification History

`GET /notifications`

- yêu cầu authentication JWT
- tenantId và userId được extract từ claims JWT
- query `notification_history` theo `tenantId` và `userId`
- hỗ trợ `limit`, mặc định `50`, clamp trong khoảng `1..200`

### 6.2 Notification Preferences

`GET /notification-preferences`

- trả preference của user hiện tại theo tenant hiện tại

`PUT /notification-preferences`

- nhận danh sách `{ channel, enabled }`
- upsert từng channel preference cho tenant/user hiện tại
- trả lại danh sách preference sau update

Nếu JWT không có `tenantId` hoặc `userId`, controller hiện trả danh sách rỗng thay vì trả lỗi 4xx.

## 7. Template and Rendering Model

Template được resolve bởi `TemplatePathResolver`.

Rule hiện tại:

- canonical default folder luôn là `templates/default/`
- tenant override path là `templates/tenant-<tenantId>/`
- default version mặc định `v1`
- template name không được path traversal

Template business đang có:

- `order-confirmation`
- `order-cancelled`
- `payment-succeeded`
- `payment-failed`

## 8. Persistence Model

### 8.1 Local Tables

- `notification_history`
- `notification_preferences`
- `processed_notifications`
- `processed_events`
- `processed_events_v2`
- `processed_notifications_v2`
- `shedlock`

### 8.2 Library-Managed Outbox

Service phụ thuộc `event-infra` và migration local tạo thêm:

- `kafka_outbox`

Bảng này được dùng cho internal republish và dispatch publish do shared infra library quản lý.

## 9. Idempotency and Safety

Service có ba lớp idempotency khác nhau:

### 9.1 Internal Event Idempotency

`NotificationInternalEventConsumer` dùng `IdempotencyService` để dedupe internal event trước ingestion.

### 9.2 Per-Channel Delivery Idempotency

Worker dùng `processed_notifications` để ngăn gửi lại cùng một event trên cùng channel.

### 9.3 Tenant-Scoped Composite Keys

Worker không dùng raw `eventId` trực tiếp mà prepend `tenantId` vào idempotency key. Điều này tránh việc hai tenant khác nhau dùng cùng `eventId` bị dedupe nhầm ở layer channel worker.

## 10. Retry, DLQ, and Backpressure

`application.yml` cho thấy service có topology retry/DLQ và replay:

- `notification.retry.1m`
- `notification.retry.5m`
- `notification.retry.30m`
- `notification.dlq`

Ngoài ra còn có cờ:

- `notification.dlq.replay.enabled`
- `notification.dlq-replay.enabled`

Trong module hiện tại, chi tiết publisher/consumer của retry queue và DLQ replay chủ yếu nằm ở shared infra hoặc test/support classes. Doc này chỉ khẳng định topology cấu hình đang tồn tại, không suy diễn thêm contract runtime ngoài phần code module hiện có.

## 11. Security and Multi-Tenancy

- Service chạy như OAuth2 resource server với JWT issuer từ Keycloak.
- Tất cả endpoint trừ health/info/prometheus đều yêu cầu authentication.
- Tenant/user identity cho REST API được lấy từ claim JWT:
  - `tenantId` hoặc `tenant_id`
  - `userId` hoặc `user_id`
- Event processing không tin tenant header từ Kafka; tenant được đọc từ payload `data.tenantId`.
- Repository query cho history và preference đều filter theo tenant.

## 12. External Dependencies

Service phụ thuộc các integration nội bộ:

- `order-view-service` để resolve `orderId -> userId`
- `user-service` để resolve contact của user
- Redis để cache contact khi bật `notification.redis-cache.enabled`
- external providers cho Email/SMS/Push

## 13. Error Semantics

Module hiện không có `@RestControllerAdvice` riêng. Vì vậy:

- validation/security error của REST sẽ theo default Spring handling
- event processing lỗi parse/validation sẽ ném `InvalidEventPayloadException`
- dependency tạm thời unavailable có thể ném `RetryableDependencyException`
- worker provider lỗi non-retryable sẽ được ghi `FAILED` và acknowledge
- worker provider lỗi retryable sẽ rethrow để Kafka retry/DLQ path xử lý

## 14. Observability

Service tích hợp:

- Micrometer metrics
- OpenTelemetry tracing
- JSON logging
- MDC propagation

Field correlation chính:

- `eventId`
- `tenantId`
- `userId`
- `orderId`
- `channel`
- `priority`
- `provider`
- `traceId`
- `correlationId`
- `notificationId`

## 15. Tech Stack

| Area | Value |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.2 |
| Build | Maven |
| Database | PostgreSQL |
| Cache | Redis |
| Messaging | Apache Kafka |
| Template Engine | Thymeleaf |
| Security | Spring Security OAuth2 Resource Server |
| Scheduling Lock | ShedLock 6.3.1 |
| Shared Infra | `event-contract:1.0.0`, `event-infra:1.0.0` |

## 16. Known Limitations

- REST API thiếu custom exception mapping nên HTTP error contract phần lớn vẫn là Spring default.
- Internal event idempotency implementation nằm sau interface/library boundary, không thể xem hết chỉ từ module code.
- Cấu hình retry/DLQ khá rộng nhưng không phải toàn bộ path được hiện thực trong module local; một phần nằm trong shared infra.
- `NotificationHistoryController` và `NotificationPreferenceController` trả danh sách rỗng khi JWT thiếu `tenantId` hoặc `userId`, thay vì từ chối request rõ ràng.

## 17. Related Artifacts

- [API spec](./api/notification.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
