# Payment Service

## 1. Purpose

`payment-service` chịu trách nhiệm xử lý thanh toán cho order sau khi inventory đã reserve thành công. Service này cung cấp một REST command API để xử lý thanh toán trực tiếp, đồng thời có thể tham gia flow saga bằng cách consume `StockReservedEvent` từ Kafka, tạo `Payment` aggregate, gọi payment provider, rồi phát event kết quả qua transactional outbox.

## 2. Key Functions

- Xử lý lệnh thanh toán qua `POST /api/payments`.
- Truy vấn trạng thái payment qua `GET /api/payments/{paymentId}`.
- Consume `inventory.events` và `inventory.events.retry` khi bật `payment.kafka.consumer.enabled=true`.
- Enforce request idempotency bằng canonical header `Idempotency-Key`, backed by unique `idempotency_key` trên bảng `payments`.
- Enforce consumer idempotency bằng bảng `processed_events`.
- Publish `payment.processing`, `payment.completed`, `payment.failed` qua outbox sang topic `payment.events`.
- Reconcile payment bị kẹt ở trạng thái `PROCESSING`.
- Timeout payment saga bị treo và phát `payment.failed` với reason `SAGA_TIMEOUT`.

## 3. Domain Model

### 3.1 Payment Aggregate

Aggregate `Payment` được lưu trong bảng `payments` với khóa chính `payment_id` và optimistic lock bằng cột `version`.

Các trường nghiệp vụ chính:

- `paymentId`
- `orderId`
- `tenantId`
- `amount`
- `currency`
- `status`
- `provider`
- `transactionId`
- `idempotencyKey`
- `correlationId`
- `traceId`
- `createdAt`
- `updatedAt`

### 3.2 Payment State Machine

Trạng thái hiện có trong code:

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

Transition hợp lệ trong aggregate:

- `PENDING -> PROCESSING`
- `PROCESSING -> SUCCEEDED`
- `PROCESSING -> FAILED`
- `PENDING -> CANCELLED`

Service hiện tại không có public cancel API. Trạng thái `CANCELLED` chỉ được phản ánh ở domain model và fallback response khi event Kafka đã được đánh dấu processed nhưng payment record không còn tìm thấy.

## 4. Request Flows

### 4.1 REST Payment Command

`POST /api/payments` nhận `PaymentProcessRequest` và bắt buộc có header `Idempotency-Key`.

`Idempotency-Key` là business idempotency source duy nhất cho REST command này. `X-Request-Id` và `X-Correlation-Id` có thể vẫn được upstream propagate cho tracing, nhưng không thay thế hoặc fallback cho deduplication key.

Body `PaymentProcessRequest` gồm:

- `orderId`
- `tenantId`
- `amount`
- `currency`
- `paymentProvider`
- `correlationId`
- `traceId`

Flow xử lý:

1. Đọc `Idempotency-Key` từ HTTP header và dùng nó làm REST idempotency key duy nhất.
2. Không đọc `idempotencyKey` từ request body và không fallback sang `X-Request-Id`.
3. Tìm payment theo idempotency key đó.
4. Nếu đã tồn tại và status là terminal (`SUCCEEDED`, `FAILED`, `CANCELLED`) thì trả lại đúng aggregate hiện có.
5. Nếu chưa tồn tại thì tạo payment mới ở trạng thái `PENDING`.
6. Nếu `payment.processing.enabled=false`:
   - payment được chuyển sang `PROCESSING`
   - sau đó bị đánh dấu `FAILED`
   - enqueue `payment.failed` với reason `PROCESSING_DISABLED`
7. Nếu processing bật:
   - chuyển `PENDING -> PROCESSING`
   - enqueue `payment.processing`
   - chạy fraud check
   - nếu `REJECT` hoặc `REVIEW` thì đánh dấu `FAILED` và enqueue `payment.failed`
   - nếu qua fraud check thì gọi payment provider với retry Resilience4j
   - nếu provider trả transaction id thì đánh dấu `SUCCEEDED` và enqueue `payment.completed`
   - nếu provider decline thì đánh dấu `FAILED` và enqueue `payment.failed`
   - nếu provider timeout thì ném `PaymentProviderTimeoutException` ra HTTP layer, không cưỡng bức đổi status sang `FAILED`

### 4.2 Kafka Saga Flow

Khi bật consumer qua `payment.kafka.consumer.enabled=true`, service consume `BaseEvent<StockReservedEvent>` từ:

- `${payment.kafka.inventory-topic}` mặc định `inventory.events`
- `${payment.kafka.retry-topic}` mặc định `inventory.events.retry`

Flow xử lý:

1. Parse JSON envelope thành `BaseEvent<StockReservedEvent>`.
2. Validate envelope và payload bắt buộc.
3. Kiểm tra `processed_events`.
4. Nếu event đã xử lý:
   - tìm payment theo `idempotencyKey`
   - nếu có thì trả lại payment hiện tại
   - nếu không có thì trả một `PaymentResponse` terminal với status `CANCELLED`
5. Nếu event chưa xử lý:
   - chạy cùng core payment flow như REST
   - ghi marker vào `processed_events` trong cùng transaction

### 4.3 Retry, DLQ, and Poison Messages

Listener container dùng `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.

- Message parse/validation lỗi sẽ ném `NonRetryableMessageException` và đi thẳng DLQ.
- `PaymentProcessingDisabledException` cũng được cấu hình là non-retryable, dù path hiện tại chủ yếu fail mềm bằng event `payment.failed`.
- Lỗi từ main topic `inventory.events` sẽ được republish sang retry topic.
- Lỗi từ retry topic sẽ bị chuyển sang DLQ.

## 5. Idempotency Contract

### 5.1 REST Idempotency

Implementation hiện tại chưa dùng bảng idempotency riêng theo chuẩn SDD baseline của repo. Thay vào đó:

- `Idempotency-Key` là contract HTTP bắt buộc cho REST write API
- `Idempotency-Key` là REST business idempotency contract duy nhất
- REST layer không đọc `idempotencyKey` từ body và không fallback sang `X-Request-Id`
- `X-Request-Id` chỉ thuộc lớp tracing/observability, không phải payment deduplication key
- unique key nằm ở `payments.idempotency_key`
- request lặp lại sẽ trả lại aggregate hiện có
- không có trạng thái `PROCESSING/COMPLETED/FAILED` trong bảng idempotency riêng
- không cache serialized response riêng
- không có TTL cleanup job cho REST idempotency

Doc này mô tả đúng implementation hiện có, không giả định behavior chưa được code.

### 5.2 Consumer Idempotency

Consumer idempotency dùng bảng `processed_events`.

- `alreadyProcessed(eventId)` kiểm tra `existsByEventId(eventId)`
- `markProcessed(eventId)` insert marker sau khi business mutation thành công
- marker được ghi trong cùng transaction với payment mutation và outbox write

Lưu ý: schema hiện tại của `processed_events` dùng `event_id` làm primary key đơn, còn `consumer_service` chỉ là cột metadata. Nghĩa là uniqueness không còn theo cặp `(event_id, consumer_service)` như baseline chung của repo.

## 6. Outbound Events

Service publish ba event type:

- `payment.processing`
- `payment.completed`
- `payment.failed`

Các event đều được tạo bởi `EventFactory("payment-service", ...)`, validate schema trước khi lưu, rồi insert vào `payment_outbox` với status `NEW`. Kafka publisher nền sẽ publish sang `${payment.kafka.events-topic}` mặc định `payment.events`.

## 7. Outbox Publishing

Outbox pattern ở service này là two-phase publishing:

1. Transaction nghiệp vụ insert/update `payments` và insert `payment_outbox`.
2. `PaymentOutboxPublisher` chạy scheduler, claim batch `NEW` bằng `FOR UPDATE SKIP LOCKED`.
3. Record được đổi sang `PROCESSING`.
4. Publisher gửi Kafka và set header từ envelope.
5. Khi broker ack:
   - outbox đổi sang `PUBLISHED`
   - `published=true`
   - `publishedAt` được set
6. Nếu publish lỗi:
   - tăng `publishAttempts`
   - set `nextAttemptAt`
   - ghi `lastError`
   - trả record về `NEW`

Distributed lock:

- job name: `payment-outbox-publisher`

## 8. Scheduled Jobs

### 8.1 Payment Reconciliation

`PaymentReconciliationJob` chạy theo `payment.reconciliation.interval-ms` mặc định `60000`.

- lock name: `payment-reconciliation`
- quét payment `PROCESSING` cũ hơn `payment.reconciliation.processing-cutoff-minutes` mặc định `5`
- nếu provider trả `SUCCEEDED` thì cập nhật `SUCCEEDED` và phát `payment.completed`
- nếu provider trả `FAILED` thì cập nhật `FAILED` và phát `payment.failed` với reason `RECONCILE_FAILED`

### 8.2 Saga Timeout Monitor

`PaymentSagaTimeoutMonitor` chạy theo `payment.saga-timeout.interval-ms` mặc định `60000`.

- lock name: `payment-saga-timeout`
- quét payment `PROCESSING` cũ hơn `payment.saga-timeout.minutes` mặc định `15`
- đánh dấu `FAILED`
- phát `payment.failed` với reason `SAGA_TIMEOUT`

## 9. Error Semantics

REST layer hiện map lỗi như sau:

- `PaymentNotFoundException` -> `404`
- `PaymentOptimisticLockException` -> `409`
- `PaymentProviderTimeoutException` -> `504`
- `MethodArgumentNotValidException` -> `400`
- `NonRetryableMessageException` -> `400`
- `PaymentDomainException` -> `400`
- lỗi còn lại -> `500`

Response body dùng `ErrorResponse`:

- `code`
- `message`
- `timestamp`

## 10. Multi-Tenancy and Security Notes

- Payment command REST yêu cầu `tenantId` trong body.
- Payment aggregate lưu `tenantId` để correlation và event emission.
- Query endpoint `GET /api/payments/{paymentId}` hiện load trực tiếp bằng `findById(paymentId)` và không filter theo tenant.
- Service hiện không có `UserContextFilter` hay auth config riêng trong module này; authentication/identity enforcement được kỳ vọng ở layer phía trước.

## 11. Observability

Service tích hợp:

- Micrometer Prometheus
- OpenTelemetry OTLP exporter
- JSON logging qua `logstash-logback-encoder`
- custom Kafka producer/consumer interceptors

Metrics nổi bật:

- payment request/success/failure/latency metrics
- Kafka consumer processing/error metrics
- outbox backlog, retry, publish latency metrics

Trace/correlation data được lưu và propagate qua:

- `correlationId`
- `traceId`
- Kafka headers
- MDC trong consumer path

## 12. Tech Stack

| Area | Value |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.2 |
| Build | Maven |
| Database | PostgreSQL |
| Migration | Flyway |
| Messaging | Apache Kafka |
| Resilience | Resilience4j Retry |
| Scheduling Lock | ShedLock |
| Telemetry | Micrometer + OpenTelemetry |

## 13. Known Limitations

- REST idempotency chưa theo bảng idempotency riêng như chuẩn SDD baseline của repo.
- Query endpoint chưa tenant-scope.
- `processed_events` hiện dùng primary key đơn theo `event_id`, không cho phép cùng `event_id` được xử lý độc lập bởi nhiều `consumer_service` trong cùng database.
- `payment.kafka.retry-topic` và `payment.kafka.dlq-topic` là inbound topic config; các `NewTopic` bean hiện lại derive retry/DLQ cho outbound `payment.events`, nên naming contract cần đọc theo code config thực tế thay vì suy diễn.

## 14. Related Artifacts

- [API spec](./api/payment.yaml)
- [Event spec](./events/payment-events.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
