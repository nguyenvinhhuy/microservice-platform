package huynv.fileservice.event;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.file.FileEventTypes;
import huynv.event.file.FileUploadedEvent;
import huynv.event.idempotency.IdempotencyService;
import huynv.fileservice.config.FileServiceProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Consumes uploaded-file events and orchestrates asynchronous malware scanning in a replay-safe manner.
 */
@Component
@ConditionalOnProperty(prefix = "file-service.scan", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class ScanOrchestrator {

    private final ObjectMapper objectMapper;
    private final FileEventSchemaValidator fileEventSchemaValidator;
    private final IdempotencyService idempotencyService;
    private final ScanResultHandler scanResultHandler;

    /**
     * Creates a scan orchestrator that listens for uploaded-file events.
     *
     * @param objectMapper ObjectMapper used to deserialize event envelopes.
     * @param fileEventSchemaValidator Event schema validator used before processing.
     * @param idempotencyService Consumer idempotency service backed by processed_events.
     * @param scanResultHandler Scan handler used to execute the scanner workflow.
     * @return Initializes the scan orchestrator.
     */
    public ScanOrchestrator(
            ObjectMapper objectMapper,
            FileEventSchemaValidator fileEventSchemaValidator,
            IdempotencyService idempotencyService,
            ScanResultHandler scanResultHandler
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.fileEventSchemaValidator = Objects.requireNonNull(fileEventSchemaValidator, "fileEventSchemaValidator");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.scanResultHandler = Objects.requireNonNull(scanResultHandler, "scanResultHandler");
    }

    /**
     * Handles a single uploaded-file event and triggers malware scanning when the event has not been processed before.
     *
     * @param record Kafka consumer record containing the serialized uploaded-file event.
     * @return Performs a side effect by scanning the uploaded object and marking the event as processed.
     */
    @Transactional
    @KafkaListener(topics = "${file-service.kafka.events-topic}", groupId = "${file-service.scan.worker-consumer-group-id}")
    public void handleUploaded(ConsumerRecord<String, String> record) {
        try {
            BaseEvent<FileUploadedEvent> event = deserialize(record.value());
            if (!FileEventTypes.FILE_UPLOADED_V1.equals(event.eventType())) {
                return;
            }
            if (idempotencyService.alreadyProcessed(event.eventId())) {
                return;
            }
            fileEventSchemaValidator.validate(event);
            scanResultHandler.handleUploadedEvent(event);
            idempotencyService.markProcessed(event.eventId());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process uploaded-file scan orchestration event.", ex);
        }
    }

    /**
     * Deserializes an uploaded-file event envelope from JSON.
     *
     * @param json Serialized event envelope.
     * @return Returns the deserialized uploaded-file event.
     */
    private BaseEvent<FileUploadedEvent> deserialize(String json) throws Exception {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(BaseEvent.class, FileUploadedEvent.class);
        return objectMapper.readValue(json, javaType);
    }
}

