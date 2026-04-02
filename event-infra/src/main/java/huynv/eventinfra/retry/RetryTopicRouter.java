package huynv.eventinfra.retry;

import huynv.eventinfra.config.NotificationProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.time.Duration;
import java.util.Objects;

/**
 * Routes failures to retry topics or the DLQ based on retry attempt and exception classification.
 */
public class RetryTopicRouter {

    private final NotificationProperties properties;
    private final RetryBackoffPolicy backoffPolicy;

    /**
     * Creates a router for selecting retry topics and enriching retry headers.
     *
     * @param properties Notification properties containing topic names.
     * @param backoffPolicy Backoff policy used to select retry tier delays.
     * @return Initializes a retry topic router.
     */
    public RetryTopicRouter(NotificationProperties properties, RetryBackoffPolicy backoffPolicy) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.backoffPolicy = Objects.requireNonNull(backoffPolicy, "backoffPolicy");
    }

    /**
     * Resolves the destination topic partition for a failed record.
     *
     * @param record Failed consumer record.
     * @param nextAttempt Attempt number to schedule.
     * @return Returns the destination topic partition.
     */
    public TopicPartition destination(ConsumerRecord<?, ?> record, int nextAttempt) {
        Objects.requireNonNull(record, "record");
        Duration delay = backoffPolicy.nextDelay(nextAttempt);
        if (delay == null) {
            return new TopicPartition(properties.getKafka().getDlqTopic(), record.partition());
        }
        String retryTopic = toRetryTopic(delay);
        return new TopicPartition(retryTopic, record.partition());
    }

    /**
     * Builds retry headers for a rerouted record including retry timing and original location metadata.
     *
     * @param record Failed consumer record.
     * @param exception Failure exception.
     * @param nextAttempt Attempt number to schedule.
     * @return Returns headers to attach to the produced retry record.
     */
    public RecordHeaders buildRetryHeaders(ConsumerRecord<?, ?> record, Exception exception, int nextAttempt) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(exception, "exception");

        RecordHeaders headers = new RecordHeaders();
        long now = System.currentTimeMillis();
        Long firstSeen = RetryHeaders.readLong(record.headers(), RetryHeaders.FIRST_SEEN_AT_MS);
        if (firstSeen == null) {
            firstSeen = now;
        }

        Duration delay = backoffPolicy.nextDelay(nextAttempt);
        long dueAt = now + (delay == null ? 0L : delay.toMillis());

        headers.add(RetryHeaders.ATTEMPT, RetryHeaders.toLongBytes(nextAttempt));
        headers.add(RetryHeaders.FIRST_SEEN_AT_MS, RetryHeaders.toLongBytes(firstSeen));
        headers.add(RetryHeaders.RETRY_DUE_AT_MS, RetryHeaders.toLongBytes(dueAt));
        headers.add(RetryHeaders.RETRY_TARGET_TOPIC, RetryHeaders.toUtf8(resolveRetryTargetTopic(record.topic())));
        headers.add(RetryHeaders.ORIGINAL_TOPIC, RetryHeaders.toUtf8(record.topic()));
        headers.add(RetryHeaders.ORIGINAL_PARTITION, RetryHeaders.toLongBytes(record.partition()));
        headers.add(RetryHeaders.ORIGINAL_OFFSET, RetryHeaders.toLongBytes(record.offset()));
        headers.add(RetryHeaders.ERROR_CLASS, RetryHeaders.toUtf8(exception.getClass().getName()));
        headers.add(RetryHeaders.ERROR_MESSAGE, RetryHeaders.toUtf8(truncate(exception.getMessage(), 1024)));
        return headers;
    }

    /**
     * Resolves the retry target topic for the given source topic.
     *
     * @param sourceTopic Source topic where the failure occurred.
     * @return Returns the topic that retry consumers should forward messages back to.
     */
    public String resolveRetryTargetTopic(String sourceTopic) {
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        if (sourceTopic.equals(properties.getKafka().getOrderTopic()) || sourceTopic.equals(properties.getKafka().getPaymentTopic())) {
            return properties.getKafka().getEventsTopic();
        }
        return sourceTopic;
    }

    /**
     * Maps a resolved delay to the appropriate retry tier topic.
     *
     * @param delay Retry delay requested.
     * @return Returns the retry tier topic name.
     */
    private String toRetryTopic(Duration delay) {
        if (delay.compareTo(Duration.ofMinutes(1)) <= 0) {
            return properties.getKafka().getRetry1mTopic();
        }
        if (delay.compareTo(Duration.ofMinutes(5)) <= 0) {
            return properties.getKafka().getRetry5mTopic();
        }
        return properties.getKafka().getRetry30mTopic();
    }

    /**
     * Truncates a string value to a maximum number of characters.
     *
     * @param value String value to truncate.
     * @param maxLen Maximum number of characters to keep.
     * @return Returns the truncated string or null when value is null.
     */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}

