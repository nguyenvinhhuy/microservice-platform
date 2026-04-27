package huynv.dlqreplayerservice.event;

import huynv.dlqreplayerservice.service.DlqStorageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Consumes dead-letter topics and stores records for manual inspection.
 */
@Component
public class DlqConsumer {

    private final DlqStorageService dlqStorageService;

    /**
     * Creates a DLQ consumer that persists inbound dead-letter records.
     *
     * @param dlqStorageService Storage service used to persist DLQ events.
     * @return Initializes a DLQ consumer instance.
     */
    public DlqConsumer(DlqStorageService dlqStorageService) {
        this.dlqStorageService = Objects.requireNonNull(dlqStorageService, "dlqStorageService");
    }

    /**
     * Consumes DLQ records from all topics matching the configured pattern.
     *
     * @param record Kafka record received from a DLQ topic.
     * @return Performs side effects by persisting the DLQ record when not previously stored.
     */
    @KafkaListener(topicPattern = "${dlq.topic-pattern:.*\\\\.dlq}", groupId = "${dlq.consumer.group-id:dlq-replayer-service}")
    public void onDlqRecord(ConsumerRecord<String, String> record) {
        try {
            putMdc(record);
            dlqStorageService.store(record);
        } finally {
            clearMdc();
        }
    }

    /**
     * Populates MDC fields from DLQ record metadata and safe propagated headers for structured troubleshooting logs.
     *
     * @param record Kafka dead-letter record currently being stored.
     * @return Performs side effects by setting MDC values for the current thread.
     */
    private static void putMdc(ConsumerRecord<String, String> record) {
        putIfPresent("topic", record == null ? null : record.topic());
        putIfPresent("partition", record == null ? null : String.valueOf(record.partition()));
        putIfPresent("offset", record == null ? null : String.valueOf(record.offset()));
        putIfPresent("eventId", header(record, "eventId"));
        putIfPresent("correlationId", header(record, "correlationId"));
        putIfPresent("tenantId", header(record, "tenantId"));
        putIfPresent("orderId", header(record, "orderId"));
        putIfPresent("productId", header(record, "productId"));
        putIfPresent("paymentId", header(record, "paymentId"));
        putIfPresent("originalTopic", firstNonBlank(header(record, "x-original-topic"), header(record, "originalTopic")));
    }

    /**
     * Clears MDC fields used during DLQ ingestion to avoid leaking context across threads.
     *
     * @return Performs side effects by removing MDC keys for the current thread.
     */
    private static void clearMdc() {
        MDC.remove("topic");
        MDC.remove("partition");
        MDC.remove("offset");
        MDC.remove("eventId");
        MDC.remove("correlationId");
        MDC.remove("tenantId");
        MDC.remove("orderId");
        MDC.remove("productId");
        MDC.remove("paymentId");
        MDC.remove("originalTopic");
    }

    /**
     * Reads a Kafka header as a UTF-8 string.
     *
     * @param record Kafka record holding the headers.
     * @param name Header name to read.
     * @return Returns the decoded header value, or null when absent.
     */
    private static String header(ConsumerRecord<String, String> record, String name) {
        if (record == null || record.headers() == null || name == null) {
            return null;
        }
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Chooses the first non-blank value from the provided candidates.
     *
     * @param values Candidate string values ordered by preference.
     * @return Returns the first non-blank value, or null when all values are blank.
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Writes one MDC entry only when the value is non-null and non-blank.
     *
     * @param key MDC key name.
     * @param value MDC value to set.
     * @return Performs a side effect by updating MDC for the current thread.
     */
    private static void putIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
    }
}

