package huynv.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.productservice.model.OutboxEvent;
import huynv.productservice.model.OutboxStatus;
import huynv.productservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Enqueues product integration events into the transactional outbox.
 */
@Service
public class ProductOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final EventFactory eventFactory;
    private final JsonSchemaValidationService schemaValidationService;

    /**
     * Creates an outbox enqueue service for product integration events.
     *
     * @param outboxEventRepository repository used to persist outbox rows.
     * @param objectMapper object mapper used to serialize payloads deterministically.
     * @param eventFactory event factory used to build unified event envelopes.
     * @param schemaValidationService Schema validation service used to validate and register event schemas.
     * @return Initializes a product outbox service instance.
     */
    public ProductOutboxService(OutboxEventRepository outboxEventRepository,
                                ObjectMapper objectMapper,
                                EventFactory eventFactory,
                                JsonSchemaValidationService schemaValidationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.eventFactory = eventFactory;
        this.schemaValidationService = schemaValidationService;
    }

    /**
     * Enqueues an integration event payload as an outbox row stored in the same transaction as the mutation.
     *
     * @param aggregateType aggregate type name used for routing and diagnosis.
     * @param aggregateId aggregate identifier used as Kafka partition key.
     * @param eventType event type string used by consumers.
     * @param payload payload object to serialize and store immutably.
     * @param correlationId correlation identifier propagated across services.
     * @param idempotencyKey idempotency key used to deduplicate producer-side effects when present.
     * @return persists a new outbox row in PENDING status.
     */
    public OutboxEvent enqueue(String aggregateType,
                               String aggregateId,
                               String eventType,
                               Object payload,
                               String correlationId,
                               String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now();
        BaseEvent<Object> envelope = eventFactory.create(
                eventType,
                aggregateId,
                0L,
                eventType + ".v1",
                correlationId,
                null,
                payload
        );
        return outboxEventRepository.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .type(envelope.eventType())
                .payload(toJson(envelope))
                .status(OutboxStatus.PENDING)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * Serializes a payload object to JSON for immutable outbox storage.
     *
     * @param payload payload object to serialize.
     * @return returns JSON string payload for outbox persistence.
     */
    private String toJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (payload instanceof BaseEvent<?> envelope && envelope.dataSchema() != null && !envelope.dataSchema().isBlank()) {
                schemaValidationService.validateAndRegister(envelope.dataSchema(), json);
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize product outbox payload.", ex);
        }
    }
}


