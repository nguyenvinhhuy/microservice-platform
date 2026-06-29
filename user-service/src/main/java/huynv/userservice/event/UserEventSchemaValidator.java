package huynv.userservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.schema.JsonSchemaValidationService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Validates user-service event envelopes before they are persisted to the transactional outbox.
 */
@Component
public class UserEventSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaValidationService jsonSchemaValidationService;

    /**
     * Creates an event validator backed by the shared JSON Schema validation service.
     *
     * @param objectMapper Object mapper used to serialize envelopes for schema validation.
     * @param jsonSchemaValidationService Shared JSON Schema validation service.
     * @return Initializes a user event schema validator instance.
     */
    public UserEventSchemaValidator(ObjectMapper objectMapper, JsonSchemaValidationService jsonSchemaValidationService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.jsonSchemaValidationService = Objects.requireNonNull(jsonSchemaValidationService, "jsonSchemaValidationService");
    }

    /**
     * Validates the canonical envelope fields and payload schema for a user-service event.
     *
     * @param event Event envelope to validate before enqueueing.
     * @return Performs a side effect by throwing when the envelope or payload violates the shared contract.
     */
    public void validate(BaseEvent<?> event) {
        Objects.requireNonNull(event, "event");
        requireNonBlank(event.eventId(), "eventId");
        requireNonBlank(event.eventType(), "eventType");
        requireNonBlank(event.aggregateId(), "aggregateId");
        requireNonBlank(event.dataSchema(), "dataSchema");
        if (!event.eventType().equals(event.dataSchema())) {
            throw new IllegalStateException("Event dataSchema must exactly match eventType.");
        }
        JsonNode root = objectMapper.valueToTree(event);
        JsonNode tenantIdNode = root.path("data").path("tenantId");
        requireNonBlank(tenantIdNode.asText(null), "data.tenantId");
        jsonSchemaValidationService.validateAndRegister(event.dataSchema(), serialize(event));
    }

    /**
     * Serializes an event envelope to JSON for schema validation.
     *
     * @param event Event envelope to serialize.
     * @return Returns the serialized JSON event document.
     */
    private String serialize(BaseEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize event envelope for schema validation.", ex);
        }
    }

    /**
     * Verifies that a required string value is present and non-blank.
     *
     * @param value Candidate string value.
     * @param fieldName Logical field name used in validation messages.
     * @return Performs a side effect by throwing when the value is blank.
     */
    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required event field '" + fieldName + "'.");
        }
    }
}

