package huynv.userservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.event.user.UserAddressCreatedEvent;
import huynv.event.user.UserCreatedEvent;
import huynv.event.user.UserEventTypes;
import huynv.event.user.UserPreferencesUpdatedEvent;
import huynv.event.user.UserUpdatedEvent;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.userservice.config.UserServiceProperties;
import huynv.userservice.domain.UserAddressEntity;
import huynv.userservice.domain.UserEntity;
import huynv.userservice.domain.UserPreferencesEntity;
import huynv.userservice.metrics.UserMetrics;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists user domain events to the shared transactional outbox using the canonical event-contract envelope.
 */
@Component
public class UserEventPublisher {

    private final EventFactory eventFactory;
    private final KafkaOutboxService kafkaOutboxService;
    private final ObjectMapper objectMapper;
    private final UserServiceProperties properties;
    private final UserMetrics userMetrics;
    private final UserEventSchemaValidator userEventSchemaValidator;

    /**
     * Creates an event publisher backed by the shared transactional outbox service.
     *
     * @param eventFactory Event factory used to build canonical BaseEvent envelopes.
     * @param kafkaOutboxService Shared outbox service used to persist publish requests.
     * @param objectMapper ObjectMapper used to serialize event envelopes.
     * @param properties User-service configuration properties.
     * @param userMetrics Metrics recorder used to count persisted outbox events.
     * @param userEventSchemaValidator Event validator used to enforce the shared schema contract before enqueueing.
     * @return Initializes a user event publisher instance.
     */
    public UserEventPublisher(
            EventFactory eventFactory,
            KafkaOutboxService kafkaOutboxService,
            ObjectMapper objectMapper,
            UserServiceProperties properties,
            UserMetrics userMetrics,
            UserEventSchemaValidator userEventSchemaValidator
    ) {
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
        this.kafkaOutboxService = Objects.requireNonNull(kafkaOutboxService, "kafkaOutboxService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
        this.userEventSchemaValidator = Objects.requireNonNull(userEventSchemaValidator, "userEventSchemaValidator");
    }

    /**
     * Persists a user-created event to the transactional outbox.
     *
     * @param userEntity Persisted user entity that was created.
     * @param correlationId Correlation identifier for the originating request.
     * @param causationId Causation identifier for the originating request.
     * @return Performs a side effect by persisting a user-created outbox row.
     */
    public void publishUserCreated(UserEntity userEntity, String correlationId, String causationId) {
        Objects.requireNonNull(userEntity, "userEntity");
        UserCreatedEvent payload = new UserCreatedEvent(
                userEntity.getId(),
                userEntity.getKeycloakUserId(),
                userEntity.getTenantId(),
                userEntity.getEmail(),
                userEntity.getFullName(),
                userEntity.getPhoneNumber(),
                userEntity.getAvatarUrl(),
                userEntity.getStatus().name(),
                userEntity.getLocale(),
                userEntity.getTimezone(),
                userEntity.getCreatedAt()
        );
        publish(UserEventTypes.USER_CREATED_V1, userEntity.getTenantId(), userEntity.getId(), userEntity.getVersion(), correlationId, causationId, payload);
    }

    /**
     * Persists a user-updated event to the transactional outbox.
     *
     * @param userEntity Persisted user entity that was updated.
     * @param correlationId Correlation identifier for the originating request.
     * @param causationId Causation identifier for the originating request.
     * @return Performs a side effect by persisting a user-updated outbox row.
     */
    public void publishUserUpdated(UserEntity userEntity, String correlationId, String causationId) {
        Objects.requireNonNull(userEntity, "userEntity");
        UserUpdatedEvent payload = new UserUpdatedEvent(
                userEntity.getId(),
                userEntity.getKeycloakUserId(),
                userEntity.getTenantId(),
                userEntity.getEmail(),
                userEntity.getFullName(),
                userEntity.getPhoneNumber(),
                userEntity.getAvatarUrl(),
                userEntity.getStatus().name(),
                userEntity.getLocale(),
                userEntity.getTimezone(),
                userEntity.getUpdatedAt()
        );
        publish(UserEventTypes.USER_UPDATED_V1, userEntity.getTenantId(), userEntity.getId(), userEntity.getVersion(), correlationId, causationId, payload);
    }

    /**
     * Persists a user-preferences-updated event to the transactional outbox.
     *
     * @param preferencesEntity Persisted preferences entity that was updated.
     * @param correlationId Correlation identifier for the originating request.
     * @param causationId Causation identifier for the originating request.
     * @return Performs a side effect by persisting a user-preferences-updated outbox row.
     */
    public void publishPreferencesUpdated(UserPreferencesEntity preferencesEntity, String correlationId, String causationId) {
        Objects.requireNonNull(preferencesEntity, "preferencesEntity");
        UserPreferencesUpdatedEvent payload = new UserPreferencesUpdatedEvent(
                preferencesEntity.getUserId(),
                preferencesEntity.getTenantId(),
                preferencesEntity.isEmailEnabled(),
                preferencesEntity.isSmsEnabled(),
                preferencesEntity.isPushEnabled(),
                preferencesEntity.isMarketingEnabled(),
                preferencesEntity.getLanguage(),
                preferencesEntity.getUpdatedAt()
        );
        publish(UserEventTypes.USER_PREFERENCES_UPDATED_V1, preferencesEntity.getTenantId(), preferencesEntity.getUserId(), preferencesEntity.getVersion(), correlationId, causationId, payload);
    }

    /**
     * Persists a user-address-created event to the transactional outbox.
     *
     * @param addressEntity Persisted address entity that was created.
     * @param correlationId Correlation identifier for the originating request.
     * @param causationId Causation identifier for the originating request.
     * @return Performs a side effect by persisting a user-address-created outbox row.
     */
    public void publishAddressCreated(UserAddressEntity addressEntity, String correlationId, String causationId) {
        Objects.requireNonNull(addressEntity, "addressEntity");
        UserAddressCreatedEvent payload = new UserAddressCreatedEvent(
                addressEntity.getUserId(),
                addressEntity.getTenantId(),
                addressEntity.getId(),
                addressEntity.getLabel(),
                addressEntity.getCountry(),
                addressEntity.getCity(),
                addressEntity.getDistrict(),
                addressEntity.getAddressLine(),
                addressEntity.getPostalCode(),
                addressEntity.isDefault(),
                addressEntity.getCreatedAt()
        );
        publish(UserEventTypes.USER_ADDRESS_CREATED_V1, addressEntity.getTenantId(), addressEntity.getUserId(), addressEntity.getVersion(), correlationId, causationId, payload);
    }

    /**
     * Builds and persists a canonical event envelope in the shared outbox table.
     *
     * @param eventType Canonical event type name.
     * @param tenantId Tenant identifier used in the partition key.
     * @param userId Domain user identifier used in the partition key.
     * @param aggregateVersion Aggregate version to store in the event envelope.
     * @param correlationId Correlation identifier for the business flow.
     * @param causationId Causation identifier for the originating request or event.
     * @param payload Domain payload to serialize.
     * @return Performs a side effect by persisting an outbox row for later Kafka publishing.
     */
    private void publish(
            String eventType,
            UUID tenantId,
            UUID userId,
            long aggregateVersion,
            String correlationId,
            String causationId,
            Object payload
    ) {
        BaseEvent<Object> event = eventFactory.create(
                eventType,
                userId.toString(),
                aggregateVersion,
                eventType,
                correlationId,
                causationId,
                payload
        );
        try {
            userEventSchemaValidator.validate(event);
            kafkaOutboxService.enqueue(
                    properties.getKafka().getEventsTopic(),
                    tenantId + ":" + userId,
                    serialize(event),
                    traceHeaders(event),
                    KafkaOutboxPurpose.INTERNAL,
                    OffsetDateTime.now()
            );
            userMetrics.recordEventPublished(eventType);
        } catch (RuntimeException exception) {
            userMetrics.recordEventPublishFailed(eventType);
            throw exception;
        }
    }

    /**
     * Serializes an event envelope into JSON for durable outbox storage.
     *
     * @param event Event envelope to serialize.
     * @return Returns the serialized JSON payload.
     */
    private String serialize(BaseEvent<Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize user event payload.", ex);
        }
    }

    /**
     * Builds Kafka trace propagation headers for the outbox payload.
     *
     * @param event Canonical event envelope being persisted to the outbox.
     * @return Returns a deterministic header map for Kafka publishing.
     */
    private Map<String, String> traceHeaders(BaseEvent<Object> event) {
        Map<String, String> headers = new LinkedHashMap<>();
        putHeader(headers, "eventId", event.eventId());
        putHeader(headers, "eventType", event.eventType());
        putHeader(headers, "dataSchema", event.dataSchema());
        putHeader(headers, "aggregateId", event.aggregateId());
        putHeader(headers, "traceId", firstNonBlank(event.traceId(), MDC.get("traceId")));
        putHeader(headers, "requestId", MDC.get("requestId"));
        putHeader(headers, "traceparent", MDC.get("traceparent"));
        putHeader(headers, "tracestate", MDC.get("tracestate"));
        putHeader(headers, "correlationId", firstNonBlank(event.correlationId(), MDC.get("correlationId")));
        putHeader(headers, "causationId", firstNonBlank(event.causationId(), MDC.get("causationId")));
        return headers;
    }

    /**
     * Returns the first non-blank value from the provided candidates.
     *
     * @param candidates Candidate values ordered by preference.
     * @return Returns the first non-blank candidate, or null when all values are blank.
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Adds a non-blank header entry to the provided map.
     *
     * @param headers Header map being assembled.
     * @param key Header key to write.
     * @param value Header value to write.
     * @return Performs a side effect by mutating the header map when the value is non-blank.
     */
    private void putHeader(Map<String, String> headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(key, value);
        }
    }
}

