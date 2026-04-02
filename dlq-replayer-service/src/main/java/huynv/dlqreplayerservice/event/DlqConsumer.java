package huynv.dlqreplayerservice.event;

import huynv.dlqreplayerservice.service.DlqStorageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
        dlqStorageService.store(record);
    }
}

