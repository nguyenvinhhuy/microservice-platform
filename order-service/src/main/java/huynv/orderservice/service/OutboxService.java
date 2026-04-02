package huynv.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.orderservice.domain.OutboxEvent;
import huynv.orderservice.domain.OutboxStatus;
import huynv.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final EventFactory eventFactory;
    private final JsonSchemaValidationService schemaValidationService;

    /**
     * Stores integration event in the same local transaction as aggregate mutation.
     *
     * @param aggregateType Aggregate class name for routing and diagnosis.
     * @param aggregateId Aggregate identifier tied to the business transaction.
     * @param type Event semantic type name consumed by downstream services.
     * @param payload Event payload object that will be serialized to JSON.
     * @param correlationId Cross-service correlation key for one business flow.
     * @param causationId Parent action key that triggered this event.
     * @param idempotencyKey Request identifier used to deduplicate command side effects.
     * @return Returns a persisted outbox row guaranteeing event will publish only after commit.
     */
    public OutboxEvent enqueue(String aggregateType,
                               String aggregateId,
                               String type,
                               Object payload,
                               String correlationId,
                               String causationId,
                               String idempotencyKey) {
        BaseEvent<Object> envelope = eventFactory.create(
                toEventType(type),
                aggregateId,
                0L,
                toDataSchema(type),
                correlationId,
                causationId,
                payload
        );
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .type(envelope.eventType())
                .payload(toJson(envelope))
                .status(OutboxStatus.PENDING)
                .correlationId(correlationId)
                .causationId(causationId)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .nextAttemptAt(OffsetDateTime.now())
                .build();
        return outboxEventRepository.save(event);
    }

    /**
     * Stores integration event with an explicit aggregate version in the same local transaction.
     *
     * @param aggregateType aggregate class name for routing and diagnosis.
     * @param aggregateId aggregate identifier tied to business transaction.
     * @param aggregateVersion aggregate version used for ordering and consumer invariants.
     * @param type event semantic type name consumed by downstream services.
     * @param payload event payload object that will be serialized to JSON under the data field.
     * @param correlationId cross-service trace key for one business flow.
     * @param causationId parent action key that triggered this event.
     * @param idempotencyKey request id used to deduplicate command side effects.
     * @return persisted outbox row guaranteeing event will publish only after commit.
     */
    public OutboxEvent enqueueVersioned(String aggregateType,
                                        String aggregateId,
                                        long aggregateVersion,
                                        String type,
                                        Object payload,
                                        String correlationId,
                                        String causationId,
                                        String idempotencyKey) {
        BaseEvent<Object> envelope = eventFactory.create(
                toEventType(type),
                aggregateId,
                aggregateVersion,
                toDataSchema(type),
                correlationId,
                causationId,
                payload
        );
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .type(envelope.eventType())
                .payload(toJson(envelope))
                .status(OutboxStatus.PENDING)
                .correlationId(correlationId)
                .causationId(causationId)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .nextAttemptAt(OffsetDateTime.now())
                .build();
        return outboxEventRepository.save(event);
    }

    /**
     * Claims and marks a locked batch of due events for the outbox publisher worker.
     *
     * @param limit Maximum number of rows polled in one scheduler round.
     * @return Returns a claimed batch with status set to PROCESSING to prevent multi-replica duplicate publishing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> lockReadyEvents(int limit) {
        List<OutboxEvent> events = outboxEventRepository.lockReadyBatch(limit);
        OffsetDateTime now = OffsetDateTime.now();
        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
        }
        return outboxEventRepository.saveAll(events);
    }

    /**
     * Marks event as sent after Kafka acknowledgment to preserve at-least-once semantics.
     *
     * @param eventId Outbox row id that has been acknowledged by the broker.
     * @return Updates the outbox row status to SENT and clears retry fields.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.setStatus(OutboxStatus.SENT);
        event.setLastError(null);
        event.setProcessingStartedAt(null);
        event.setPublishedAt(OffsetDateTime.now());
        outboxEventRepository.save(event);
    }

    /**
     * Schedules retry for failed publish using exponential backoff per event row.
     *
     * @param eventId Outbox row id that failed to send.
     * @param error Error text truncated and stored for diagnostics.
     * @return Updates the outbox row status to FAILED and schedules the next attempt timestamp.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventId, String error) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        int nextRetry = event.getRetryCount() + 1;
        long delaySeconds = Math.min(60, (long) Math.pow(2, Math.min(nextRetry, 6)));
        event.setStatus(OutboxStatus.FAILED);
        event.setRetryCount(nextRetry);
        event.setLastError(trim(error, 500));
        event.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        event.setProcessingStartedAt(null);
        outboxEventRepository.save(event);
    }

    /**
     * Serializes event payload to JSON once at transaction time to avoid schema drift on send.
     *
     * @param payload Payload object to serialize for immutable outbox storage.
     * @return Returns a JSON string stored in the outbox row.
     */
    private String toJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (payload instanceof BaseEvent<?> envelope && envelope.dataSchema() != null && !envelope.dataSchema().isBlank()) {
                schemaValidationService.validateAndRegister(envelope.dataSchema(), json);
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize outbox payload", ex);
        }
    }

    /**
     * Returns the current outbox backlog size for monitoring and alerting.
     *
     * @return Returns the number of outbox rows that are pending publish or scheduled for retry.
     */
    public long outboxBacklogSize() {
        return outboxEventRepository.countByStatus(OutboxStatus.PENDING) + outboxEventRepository.countByStatus(OutboxStatus.FAILED);
    }

    /**
     * Maps legacy in-process event type identifiers to stable semantic event type names.
     *
     * @param legacyType Legacy event type name used by internal code paths.
     * @return Returns a stable eventType value used in Kafka envelopes.
     */
    private String toEventType(String legacyType) {
        return switch (legacyType) {
            case "OrderCreatedEvent" -> "order.created";
            case "OrderPaidEvent" -> "order.paid";
            case "OrderCancelledEvent" -> "order.cancelled";
            case "OrderFailedEvent" -> "order.failed";
            default -> legacyType;
        };
    }

    /**
     * Derives a schema identifier from the semantic event type and a fixed version suffix.
     *
     * @param legacyType Legacy event type name used by internal code paths.
     * @return Returns a versioned schema identifier suitable for consumers.
     */
    private String toDataSchema(String legacyType) {
        return toEventType(legacyType) + ".v1";
    }

    /**
     * Trims long error message to fixed storage size while preserving root cause prefix.
     *
     * @param input Raw error message from publish attempt.
     * @param maxLength Max persisted length for database column.
     * @return Returns a safe-to-store message string.
     */
    private String trim(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}


