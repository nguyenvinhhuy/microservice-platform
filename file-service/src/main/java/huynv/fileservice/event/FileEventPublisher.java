package huynv.fileservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.event.file.FileAvailableEvent;
import huynv.event.file.FileDeletedEvent;
import huynv.event.file.FileEventTypes;
import huynv.event.file.FileQuarantinedEvent;
import huynv.event.file.FileScanCompletedEvent;
import huynv.event.file.FileUploadedEvent;
import huynv.fileservice.domain.MalwareScanStatus;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes file lifecycle events through the shared transactional outbox infrastructure.
 */
@Component
public class FileEventPublisher {

    private final FileServiceProperties properties;
    private final EventFactory eventFactory;
    private final FileEventSchemaValidator fileEventSchemaValidator;
    private final KafkaOutboxService kafkaOutboxService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a publisher that serializes validated file lifecycle events into the shared Kafka outbox.
     *
     * @param properties File-service properties containing Kafka topic configuration.
     * @param eventFactory Event factory used to create canonical envelopes.
     * @param fileEventSchemaValidator Event schema validator used before enqueueing.
     * @param kafkaOutboxService Shared outbox service used to persist messages transactionally.
     * @param objectMapper ObjectMapper used to serialize the event envelope.
     * @return Initializes the file event publisher.
     */
    public FileEventPublisher(
            FileServiceProperties properties,
            EventFactory eventFactory,
            FileEventSchemaValidator fileEventSchemaValidator,
            KafkaOutboxService kafkaOutboxService,
            ObjectMapper objectMapper
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.fileEventSchemaValidator = Objects.requireNonNull(fileEventSchemaValidator, "fileEventSchemaValidator");
        this.kafkaOutboxService = Objects.requireNonNull(kafkaOutboxService, "kafkaOutboxService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Enqueues a file.uploaded.v1 event for asynchronous Kafka publishing.
     *
     * @param fileRecord Persisted file metadata record.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @return Performs a side effect by persisting an outbox row.
     */
    public void publishUploaded(FileRecord fileRecord, String correlationId, String causationId) {
        enqueue(fileRecord, FileEventTypes.FILE_UPLOADED_V1, correlationId, causationId, new FileUploadedEvent(
                fileRecord.getId(),
                fileRecord.getTenantId(),
                fileRecord.getOwnerUserId(),
                fileRecord.getCategory(),
                fileRecord.getBucket(),
                fileRecord.getObjectKey(),
                fileRecord.getOriginalFilename(),
                fileRecord.getContentType(),
                fileRecord.getSizeBytes(),
                fileRecord.getChecksumSha256(),
                fileRecord.getVisibility().name(),
                Instant.now()
        ));
    }

    /**
     * Enqueues a file.available.v1 event for asynchronous Kafka publishing.
     *
     * @param fileRecord Persisted file metadata record.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @return Performs a side effect by persisting an outbox row.
     */
    public void publishAvailable(FileRecord fileRecord, String correlationId, String causationId) {
        enqueue(fileRecord, FileEventTypes.FILE_AVAILABLE_V1, correlationId, causationId, new FileAvailableEvent(
                fileRecord.getId(),
                fileRecord.getTenantId(),
                fileRecord.getOwnerUserId(),
                fileRecord.getCategory(),
                fileRecord.getBucket(),
                fileRecord.getObjectKey(),
                fileRecord.getContentType(),
                fileRecord.getSizeBytes(),
                fileRecord.getChecksumSha256(),
                fileRecord.getVisibility().name(),
                Instant.now()
        ));
    }

    /**
     * Enqueues a file.deleted.v1 event for asynchronous Kafka publishing.
     *
     * @param fileRecord Persisted file metadata record.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @return Performs a side effect by persisting an outbox row.
     */
    public void publishDeleted(FileRecord fileRecord, String correlationId, String causationId) {
        enqueue(fileRecord, FileEventTypes.FILE_DELETED_V1, correlationId, causationId, new FileDeletedEvent(
                fileRecord.getId(),
                fileRecord.getTenantId(),
                fileRecord.getOwnerUserId(),
                fileRecord.getBucket(),
                fileRecord.getObjectKey(),
                Instant.now()
        ));
    }

    /**
     * Enqueues a file.quarantined.v1 event for asynchronous Kafka publishing.
     *
     * @param fileRecord Persisted file metadata record.
     * @param reason Human-readable quarantine reason.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @return Performs a side effect by persisting an outbox row.
     */
    public void publishQuarantined(FileRecord fileRecord, String reason, String correlationId, String causationId) {
        enqueue(fileRecord, FileEventTypes.FILE_QUARANTINED_V1, correlationId, causationId, new FileQuarantinedEvent(
                fileRecord.getId(),
                fileRecord.getTenantId(),
                fileRecord.getOwnerUserId(),
                fileRecord.getMalwareScanStatus().name(),
                reason,
                Instant.now()
        ));
    }

    /**
     * Enqueues a file.scan.completed.v1 event for asynchronous Kafka publishing on the dedicated scan-results topic.
     *
     * @param fileRecord Persisted file metadata record.
     * @param malwareScanStatus Malware scan outcome.
     * @param reason Human-readable scan result detail.
     * @param scanDurationMs Scan duration in milliseconds.
     * @param scannerName Scanner implementation name.
     * @param timedOut Whether the scan timed out.
     * @param checksumBlacklisted Whether the verdict came from the malicious checksum blacklist.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @return Performs a side effect by persisting an outbox row.
     */
    public void publishScanCompleted(
            FileRecord fileRecord,
            MalwareScanStatus malwareScanStatus,
            String reason,
            long scanDurationMs,
            String scannerName,
            boolean timedOut,
            boolean checksumBlacklisted,
            String correlationId,
            String causationId
    ) {
        enqueue(
                fileRecord,
                FileEventTypes.FILE_SCAN_COMPLETED_V1,
                correlationId,
                causationId,
                properties.getKafka().getScanResultsTopic(),
                new FileScanCompletedEvent(
                        fileRecord.getId(),
                        fileRecord.getTenantId(),
                        malwareScanStatus.name(),
                        reason,
                        scanDurationMs,
                        scannerName,
                        timedOut,
                        checksumBlacklisted,
                        Instant.now()
                )
        );
    }

    /**
     * Creates, validates, serializes, and enqueues a file event using the shared outbox service.
     *
     * @param fileRecord Persisted file metadata record.
     * @param eventType File event type name.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @param data File event payload.
     * @return Performs a side effect by persisting an outbox row.
     */
    private void enqueue(FileRecord fileRecord, String eventType, String correlationId, String causationId, Object data) {
        enqueue(fileRecord, eventType, correlationId, causationId, properties.getKafka().getEventsTopic(), data);
    }

    /**
     * Creates, validates, serializes, and enqueues a file event to the specified topic using the shared outbox service.
     *
     * @param fileRecord Persisted file metadata record.
     * @param eventType File event type name.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the triggering request or event.
     * @param topic Target Kafka topic.
     * @param data File event payload.
     * @return Performs a side effect by persisting an outbox row.
     */
    private void enqueue(FileRecord fileRecord, String eventType, String correlationId, String causationId, String topic, Object data) {
        try {
            BaseEvent<Object> event = eventFactory.create(
                    eventType,
                    fileRecord.getId().toString(),
                    fileRecord.getVersion(),
                    eventType,
                    correlationId,
                    causationId,
                    data
            );
            fileEventSchemaValidator.validate(event);
            kafkaOutboxService.enqueue(
                    topic,
                    fileRecord.getTenantId() + ":" + fileRecord.getId(),
                    objectMapper.writeValueAsString(event),
                    Map.of(
                            "event_type", eventType,
                            "tenant_id", fileRecord.getTenantId().toString(),
                            "aggregate_id", fileRecord.getId().toString()
                    ),
                    KafkaOutboxPurpose.INTERNAL,
                    OffsetDateTime.now()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enqueue file event type=" + eventType + ".", ex);
        }
    }
}

