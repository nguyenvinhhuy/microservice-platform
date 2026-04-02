1. Provider Timeout — ⚠️ Có 1 điểm cần sửa
- Đang dùng:
    + Future.get(timeout)
=> để timeout provider call.

- Điều này hoạt động, nhưng trong production người ta thường dùng:
    + Resilience4j TimeLimiter

=> để tích hợp với:
    + CircuitBreaker
    + Retry
    + Bulkhead

- Hiện tại đang có:
    + CircuitBreaker
    + Timeout
    + Executor

- Nhưng nếu convert sang:
    + Resilience4j TimeLimiter

→ observability tốt hơn.

- Ví dụ metric:
resilience4j_timelimiter_calls_total

✔ Không phải bug
⚠ Chỉ là improvement

---------------------------------------------------------------------------

2. DLQ replay rate limit
- Hiện tại bạn chỉ guard bằng:
    + maxReplayAttempts

- Nên thêm:
    + replayRateLimit

- ví dụ:
    + notification.dlq.replay.rateLimit = 10/s

=> để tránh:
    + DLQ replay flood