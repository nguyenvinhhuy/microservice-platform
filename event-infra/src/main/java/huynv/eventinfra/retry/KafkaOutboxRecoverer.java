package huynv.eventinfra.retry;

import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.exception.InvalidEventPayloadException;
import huynv.eventinfra.exception.NonRetryableNotificationException;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Routes failed Kafka records into the database-backed outbox for delayed retry or DLQ isolation.
 */
public class KafkaOutboxRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxRecoverer.class);

    private final NotificationProperties properties;
    private final KafkaOutboxService outboxService;
    private final NotificationMetrics metrics;
    private final RetryBackoffPolicy backoffPolicy;
    private final RetryTopicRouter topicRouter;

    /**
     * Creates a recoverer that schedules retries and DLQ publications through the transactional outbox.
     *
     * @param properties Notification properties containing retry budgets and topic names.
     * @param outboxService Outbox service used to persist scheduled publishes.
     * @param metrics Metrics used to report retry and DLQ routing events.
     * @return Initializes a Kafka outbox recoverer.
     */
    public KafkaOutboxRecoverer(NotificationProperties properties, KafkaOutboxService outboxService, NotificationMetrics metrics) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        int configuredMaxRetries = Math.max(0, properties.getRetry().getMaxAttempts() - 1);
        int maxRetries = Math.min(3, configuredMaxRetries);
        this.backoffPolicy = new RetryBackoffPolicy(maxRetries);
        this.topicRouter = new RetryTopicRouter(properties, this.backoffPolicy);
    }

    /**
     * Schedules a failed record for retry or DLQ publishing based on exception classification and retry budget.
     *
     * @param record Failed consumer record.
     * @param exception Exception that caused processing to fail.
     * @return Performs side effects by persisting an outbox row representing a retry publish or DLQ publish.
     */
    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(exception, "exception");

        String channel = channelFromHeaders(record.headers());
        Long tenantId = longHeader(record.headers(), "tenantId");
        String priority = stringHeader(record.headers(), "priority");
        String provider = stringHeader(record.headers(), "provider");

        int currentAttempt = toAttempt(record.headers());
        int nextAttempt = currentAttempt + 1;

        boolean nonRetryable = isNonRetryable(exception);
        Duration baseDelay = backoffPolicy.nextDelay(nextAttempt);
        boolean exhausted = baseDelay == null;

        if (nonRetryable || exhausted) {
            enqueueDlq(record, exception, nonRetryable, exhausted);
            return;
        }

        Duration delay = jitter(baseDelay, 0.2);
        OffsetDateTime dueAt = OffsetDateTime.now().plus(delay);
        String retryTierTopic = retryTierTopic(baseDelay);
        String retryTargetTopic = topicRouter.resolveRetryTargetTopic(record.topic());

        Map<String, String> headers = toHeaderMap(record.headers());
        headers.put(RetryHeaders.ATTEMPT, String.valueOf(nextAttempt));
        headers.put(RetryHeaders.FIRST_SEEN_AT_MS, firstSeen(record.headers()));
        headers.put(RetryHeaders.RETRY_DUE_AT_MS, String.valueOf(System.currentTimeMillis() + delay.toMillis()));
        headers.put(RetryHeaders.RETRY_TARGET_TOPIC, retryTargetTopic);
        headers.putIfAbsent(RetryHeaders.ORIGINAL_TOPIC, record.topic());
        headers.put(RetryHeaders.ORIGINAL_PARTITION, String.valueOf(record.partition()));
        headers.put(RetryHeaders.ORIGINAL_OFFSET, String.valueOf(record.offset()));
        headers.put(RetryHeaders.ERROR_CLASS, exception.getClass().getName());
        headers.put(RetryHeaders.ERROR_MESSAGE, truncate(exception.getMessage(), 1024));
        headers.put("retry_tier_topic", retryTierTopic);

        outboxService.enqueue(retryTierTopic, safeString(record.key()), safeString(record.value()), headers, KafkaOutboxPurpose.RETRY, dueAt);
        metrics.incrementRetry(record.topic(), properties.getKafka().getGroupId());
        if (channel != null) {
            metrics.incrementRetryTotal(channel, provider, tenantId, priority);
        }
        metrics.incrementRetryRate();
        log.warn("Kafka record scheduled for retry attempt={} sourceTopic={} retryTierTopic={} retryTargetTopic={} partition={} offset={} delayMs={} errorClass={} message={}",
                nextAttempt,
                record.topic(),
                retryTierTopic,
                retryTargetTopic,
                record.partition(),
                record.offset(),
                delay.toMillis(),
                exception.getClass().getName(),
                exception.getMessage());
    }

    private void enqueueDlq(ConsumerRecord<?, ?> record, Exception exception, boolean nonRetryable, boolean exhausted) {
        Map<String, String> headers = toHeaderMap(record.headers());
        headers.put(RetryHeaders.ATTEMPT, String.valueOf(toAttempt(record.headers())));
        headers.put(RetryHeaders.ERROR_CLASS, exception.getClass().getName());
        headers.put(RetryHeaders.ERROR_MESSAGE, truncate(exception.getMessage(), 1024));
        headers.put("exception_class", exception.getClass().getName());
        headers.put("exception_message", truncate(exception.getMessage(), 1024));
        headers.put("stack_hash", stackHash(exception));
        headers.putIfAbsent("original_topic", record.topic());
        headers.putIfAbsent("consumer_group", properties.getKafka().getGroupId());
        headers.put("dlq_reason", nonRetryable ? "non_retryable" : "retry_exhausted");
        headers.put("dlq_exhausted", String.valueOf(exhausted));
        headers.put("dlq_original_topic", record.topic());
        headers.put("dlq_original_partition", String.valueOf(record.partition()));
        headers.put("dlq_original_offset", String.valueOf(record.offset()));

        outboxService.enqueue(properties.getKafka().getDlqTopic(), safeString(record.key()), safeString(record.value()), headers, KafkaOutboxPurpose.DLQ, OffsetDateTime.now());
        metrics.incrementDlq(record.topic(), properties.getKafka().getGroupId());
        String channel = channelFromHeaders(record.headers());
        if (channel != null) {
            Long tenantId = longHeader(record.headers(), "tenantId");
            String priority = stringHeader(record.headers(), "priority");
            String provider = stringHeader(record.headers(), "provider");
            metrics.incrementDlqTotal(channel, provider, tenantId, priority);
        }
        log.error("Kafka record routed to DLQ topic={} originalTopic={} partition={} offset={} nonRetryable={} exhausted={} errorClass={} message={}",
                properties.getKafka().getDlqTopic(),
                record.topic(),
                record.partition(),
                record.offset(),
                nonRetryable,
                exhausted,
                exception.getClass().getName(),
                exception.getMessage());
    }

    private boolean isNonRetryable(Exception exception) {
        return exception instanceof InvalidEventPayloadException
                || exception instanceof NonRetryableNotificationException
                || exception instanceof IllegalArgumentException;
    }

    private String retryTierTopic(Duration baseDelay) {
        Objects.requireNonNull(baseDelay, "baseDelay");
        if (baseDelay.compareTo(Duration.ofMinutes(1)) <= 0) {
            return properties.getKafka().getRetry1mTopic();
        }
        if (baseDelay.compareTo(Duration.ofMinutes(5)) <= 0) {
            return properties.getKafka().getRetry5mTopic();
        }
        return properties.getKafka().getRetry30mTopic();
    }

    private static Duration jitter(Duration baseDelay, double factor) {
        Objects.requireNonNull(baseDelay, "baseDelay");
        long baseMs = Math.max(1L, baseDelay.toMillis());
        if (factor <= 0.0) {
            return Duration.ofMillis(baseMs);
        }
        double delta = baseMs * factor;
        double min = baseMs - delta;
        double max = baseMs + delta;
        double sampled = java.util.concurrent.ThreadLocalRandom.current().nextDouble(min, max);
        return Duration.ofMillis((long) Math.max(1.0, sampled));
    }

    private static int toAttempt(Headers headers) {
        if (headers == null) {
            return 0;
        }
        Header header = headers.lastHeader(RetryHeaders.ATTEMPT);
        if (header == null || header.value() == null) {
            return 0;
        }
        byte[] value = header.value();
        try {
            if (value.length == Long.BYTES) {
                long l = ByteBuffer.wrap(value).getLong();
                if (l < 0) {
                    return 0;
                }
                if (l > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return (int) l;
            }
            String s = new String(value, java.nio.charset.StandardCharsets.UTF_8);
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String firstSeen(Headers headers) {
        if (headers == null) {
            return String.valueOf(System.currentTimeMillis());
        }
        Header header = headers.lastHeader(RetryHeaders.FIRST_SEEN_AT_MS);
        if (header == null || header.value() == null) {
            return String.valueOf(System.currentTimeMillis());
        }
        try {
            byte[] value = header.value();
            if (value.length == Long.BYTES) {
                return String.valueOf(ByteBuffer.wrap(value).getLong());
            }
            return new String(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static Map<String, String> toHeaderMap(Headers headers) {
        Map<String, String> mapped = new HashMap<>();
        if (headers == null) {
            return mapped;
        }
        headers.forEach(h -> {
            if (h == null || h.key() == null) {
                return;
            }
            if (h.value() == null) {
                return;
            }
            mapped.put(h.key(), new String(h.value(), java.nio.charset.StandardCharsets.UTF_8));
        });
        return mapped;
    }

    private static String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * Reads a Kafka header value as UTF-8 text.
     *
     * @param headers Kafka headers to read from.
     * @param key Header key to resolve.
     * @return Returns the header value as a string or null when missing.
     */
    private static String stringHeader(Headers headers, String key) {
        if (headers == null || key == null) {
            return null;
        }
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Reads a Kafka header value as a Long when the header is present and parseable.
     *
     * @param headers Kafka headers to read from.
     * @param key Header key to resolve.
     * @return Returns the parsed long value or null when missing or invalid.
     */
    private static Long longHeader(Headers headers, String key) {
        String value = stringHeader(headers, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves a channel value from the record headers when present.
     *
     * @param headers Kafka headers to read from.
     * @return Returns the parsed channel type or null when missing or invalid.
     */
    private static String channelFromHeaders(Headers headers) {
        String channel = stringHeader(headers, "channel");
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim();
    }

    private static String stackHash(Exception exception) {
        if (exception == null) {
            return null;
        }
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            exception.printStackTrace(new java.io.PrintWriter(writer));
            byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }
}

