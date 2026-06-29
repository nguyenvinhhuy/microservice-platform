package huynv.fileservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.schema.JsonSchemaValidationService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Validates file-service event payloads against shared JSON schemas before publish or consume operations proceed.
 */
@Component
public class FileEventSchemaValidator {

    private final JsonSchemaValidationService jsonSchemaValidationService;
    private final ObjectMapper objectMapper;

    /**
     * Creates an event schema validator backed by the shared JSON Schema validation service.
     *
     * @param jsonSchemaValidationService Shared schema validation service.
     * @param objectMapper ObjectMapper used to serialize payloads before validation.
     * @return Initializes the file event schema validator.
     */
    public FileEventSchemaValidator(JsonSchemaValidationService jsonSchemaValidationService, ObjectMapper objectMapper) {
        this.jsonSchemaValidationService = Objects.requireNonNull(jsonSchemaValidationService, "jsonSchemaValidationService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Validates the event payload referenced by the event dataSchema field.
     *
     * @param event Event envelope to validate.
     * @return Performs a side effect by throwing when schema validation fails.
     */
    public void validate(BaseEvent<?> event) {
        Objects.requireNonNull(event, "event");
        if (!event.eventType().equals(event.dataSchema())) {
            throw new IllegalStateException("File event eventType=" + event.eventType() + " does not match dataSchema=" + event.dataSchema() + ".");
        }
        try {
            jsonSchemaValidationService.validateAndRegister(event.dataSchema(), objectMapper.writeValueAsString(event.data()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to validate file event schema for dataSchema=" + event.dataSchema() + ".", ex);
        }
    }
}

