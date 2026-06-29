package huynv.fileservice.event;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.file.FileEventTypes;
import huynv.event.file.FileScanCompletedEvent;
import huynv.event.idempotency.IdempotencyService;
import huynv.fileservice.service.FileLifecycleService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Consumes asynchronous file scan results and applies idempotent lifecycle transitions.
 */
@Component
@ConditionalOnProperty(prefix = "file-service.kafka", name = "scan-consumer-enabled", havingValue = "true", matchIfMissing = true)
public class FileScanResultConsumer {

    private final ObjectMapper objectMapper;
    private final FileEventSchemaValidator fileEventSchemaValidator;
    private final IdempotencyService idempotencyService;
    private final FileLifecycleService fileLifecycleService;

    /**
     * Creates a scan-result consumer that validates schema and enforces consumer idempotency.
     *
     * @param objectMapper ObjectMapper used to deserialize event envelopes.
     * @param fileEventSchemaValidator Event schema validator used before processing.
     * @param idempotencyService Consumer idempotency service backed by processed_events.
     * @param fileLifecycleService Lifecycle service used to apply scan transitions.
     */
    public FileScanResultConsumer(
            ObjectMapper objectMapper,
            FileEventSchemaValidator fileEventSchemaValidator,
            IdempotencyService idempotencyService,
            FileLifecycleService fileLifecycleService
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.fileEventSchemaValidator = Objects.requireNonNull(fileEventSchemaValidator, "fileEventSchemaValidator");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.fileLifecycleService = Objects.requireNonNull(fileLifecycleService, "fileLifecycleService");
    }

    /**
     * Processes a single file scan result event when it has not been seen before by this consumer.
     *
     * @param record Kafka consumer record containing the serialized event envelope.
     */
    @KafkaListener(topics = "${file-service.kafka.scan-results-topic}", groupId = "${file-service.kafka.scan-consumer-group-id}")
    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        try {
            BaseEvent<FileScanCompletedEvent> event = deserialize(record.value());
            if (!FileEventTypes.FILE_SCAN_COMPLETED_V1.equals(event.eventType())) {
                return;
            }
            if (idempotencyService.alreadyProcessed(event.eventId())) {
                return;
            }
            fileEventSchemaValidator.validate(event);
            fileLifecycleService.applyScanResult(event);
            idempotencyService.markProcessed(event.eventId());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process file scan result event.", ex);
        }
    }

    /**
     * Deserializes a file scan result envelope from JSON.
     *
     * @param json Serialized event envelope.
     * @return Returns the deserialized file scan result event.
     */
    private BaseEvent<FileScanCompletedEvent> deserialize(String json) throws Exception {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(BaseEvent.class, FileScanCompletedEvent.class);
        return objectMapper.readValue(json, javaType);
    }
}

