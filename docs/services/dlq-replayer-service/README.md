# DLQ Replayer Service

## 1. Purpose

`dlq-replayer-service` là administrative service để thu thập record từ các dead-letter topic, lưu chúng vào database để inspect, rồi cho phép replay hoặc skip thủ công. Đây không phải business service; nó là tooling vận hành cho event-driven platform.

## 2. Key Functions

- Consume mọi Kafka topic khớp pattern `.*\.dlq`.
- Persist DLQ record vào bảng `dlq_events`.
- Enforce consumer idempotency bằng `processed_events`.
- Expose admin APIs:
  - `GET /dlq/events`
  - `POST /dlq/replay`
  - `POST /dlq/skip`

## 3. Storage Model

Bảng chính là `dlq_events` với unique constraint:

- `(topic, partition, offset)`

Trường chính:

- `topic`
- `partition`
- `offset`
- `key`
- `payload`
- `headers_json`
- `original_topic`
- `status`
- `created_at`
- `updated_at`

Status machine hiện có:

- `PENDING`
- `REPLAYED`
- `SKIPPED`

## 4. DLQ Ingestion Flow

`DlqConsumer` consume theo `topicPattern = ${dlq.topic-pattern:.*\\.dlq}`.

Flow:

1. nhận record từ topic DLQ
2. build dedupe key: `topic:partition:offset`
3. check `processed_events`
4. nếu chưa xử lý:
   - persist vào `dlq_events`
   - serialize headers vào `headers_json`
   - cố extract `original_topic` từ header:
     - `kafka_dlt-original-topic`
     - fallback `dlt-original-topic`
5. mark processed trong `processed_events`

Ngoài `processed_events`, service còn có unique constraint `(topic, partition, offset)` ở `dlq_events`, nên duplicate store cũng bị chặn ở DB level.

## 5. Replay Flow

`POST /dlq/replay` gọi `DlqReplayService.replay(id, overrideTopic)`.

Behavior:

1. load `DlqEvent` theo `id`
2. chọn target topic:
   - dùng `overrideTopic` nếu có
   - nếu không thì dùng `originalTopic` đã lưu
3. nếu không có target hợp lệ thì throw `IllegalStateException`
4. publish payload qua `KafkaTemplate.send(target, key, payload)`
5. set `status = REPLAYED`

Lưu ý quan trọng:

- replay là republish payload thô, không restore metadata/offset gốc
- service không tự validate payload hay schema trước khi replay
- service không reset trạng thái nếu publish asynchronous fail sau khi method trả về vì code hiện không block chờ broker ack hoàn tất

## 6. Skip Flow

`POST /dlq/skip` chỉ:

- load record theo `id`
- set `status = SKIPPED`

Không có tombstone, purge, hay archive path trong module hiện tại.

## 7. Inspection API

`GET /dlq/events`

Query params:

- `status` mặc định `PENDING`
- `page` mặc định `0`
- `size` mặc định `50`

Response:

- page của `DlqEventResponse`

Trường response:

- `id`
- `topic`
- `partition`
- `offset`
- `key`
- `originalTopic`
- `status`
- `createdAt`

## 8. Idempotency

Consumer idempotency dùng `JdbcIdempotencyService`.

- table: `processed_events`
- key thực tế: `topic:partition:offset`
- purpose: tránh persist cùng một DLQ record nhiều lần khi re-delivery xảy ra

## 9. Security and Multi-Tenancy Notes

- module hiện không có Spring Security config riêng
- admin endpoints đang mở theo default web stack của module
- service không tenant-scope vì DLQ records là hạ tầng vận hành chung

## 10. Error Semantics

Service hiện không có `@RestControllerAdvice`.

- replay/skip với id không tồn tại -> unchecked exception mặc định
- replay thiếu `originalTopic` và không có `overrideTopic` -> `IllegalStateException`
- parse/store header serialization lỗi sẽ fallback `{}` thay vì fail request

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

- không có auth/admin authorization trong module.
- replay không chờ xác nhận publish hoàn tất trước khi mark `REPLAYED`.
- không có batch replay API.
- không có purge/archive workflow.
- không có schema validation trước replay.

## 13. Related Artifacts

- [API spec](./api/dlq-replayer.yaml)
- [Event spec](./events/dlq-replayer-events.yaml)
- [Dependencies](./dependencies.md)
- [Database schema](./database/schema.sql)
