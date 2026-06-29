package huynv.event.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates JSON payloads against JSON Schemas and optionally registers schemas in an external registry.
 */
public final class JsonSchemaValidationService {

    private final ObjectMapper objectMapper;
    private final ClasspathSchemaLoader schemaLoader;
    private final SchemaRegistryClient schemaRegistryClient;
    private final JsonSchemaFactory jsonSchemaFactory;
    private final ConcurrentHashMap<String, JsonSchema> compiledSchemas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> registeredSchemas = new ConcurrentHashMap<>();

    /**
     * Creates a validation service that loads schemas from classpath and performs optional registration.
     *
     * @param objectMapper ObjectMapper used to parse JSON values and schemas.
     * @param schemaLoader Loader used to read JSON Schema documents.
     * @param schemaRegistryClient Registry client used to register schemas when enabled.
     * @return Initializes the JSON Schema validation service.
     */
    public JsonSchemaValidationService(ObjectMapper objectMapper, ClasspathSchemaLoader schemaLoader, SchemaRegistryClient schemaRegistryClient) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaLoader = Objects.requireNonNull(schemaLoader, "schemaLoader");
        this.schemaRegistryClient = Objects.requireNonNull(schemaRegistryClient, "schemaRegistryClient");
        this.jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    /**
     * Validates a JSON value against the schema referenced by the given schema identifier.
     *
     * @param schemaId Schema identifier including version suffix.
     * @param jsonValue JSON string value to validate.
     * @return Performs a side effect by validating and potentially registering the referenced schema.
     */
    public void validateAndRegister(String schemaId, String jsonValue) {
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(jsonValue, "jsonValue");

        String schema = schemaLoader.loadSchema(schemaId);
        registerOnce(schemaId, schema);
        JsonSchema compiled = compiledSchemas.computeIfAbsent(schemaId, ignored -> compile(schemaId, schema));
        JsonNode node = parseJson(schemaId, jsonValue);
        Set<ValidationMessage> errors = compiled.validate(node);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("JSON Schema validation failed schemaId=" + schemaId + " errorCount=" + errors.size() + ".");
        }
    }

    private JsonSchema compile(String schemaId, String jsonSchema) {
        try {
            return jsonSchemaFactory.getSchema(normalizeSchemaDocument(schemaId, objectMapper.readTree(jsonSchema)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compile JSON Schema schemaId=" + schemaId + ".", ex);
        }
    }

    /**
     * Normalizes repository-local schema identifiers into absolute URNs accepted by the JSON Schema validator.
     *
     * @param schemaId Logical repository schema identifier.
     * @param schemaNode Parsed JSON Schema document.
     * @return Returns the normalized schema document that can be compiled by the validator.
     */
    private JsonNode normalizeSchemaDocument(String schemaId, JsonNode schemaNode) {
        if (!(schemaNode instanceof ObjectNode objectNode)) {
            return schemaNode;
        }
        JsonNode idNode = objectNode.get("$id");
        if (idNode == null || !idNode.isTextual()) {
            objectNode.put("$id", toAbsoluteSchemaId(schemaId));
            return objectNode;
        }
        String rawId = idNode.asText();
        try {
            URI uri = URI.create(rawId);
            if (uri.isAbsolute()) {
                return objectNode;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to repository-local normalization.
        }
        objectNode.put("$id", toAbsoluteSchemaId(rawId.isBlank() ? schemaId : rawId));
        return objectNode;
    }

    /**
     * Converts a repository-local schema identifier into a stable absolute URN.
     *
     * @param schemaId Logical schema identifier used by the repository.
     * @return Returns an absolute URN representation of the schema identifier.
     */
    private String toAbsoluteSchemaId(String schemaId) {
        return "urn:microservice-platform:schema:" + Objects.requireNonNull(schemaId, "schemaId");
    }

    private JsonNode parseJson(String schemaId, String jsonValue) {
        try {
            return objectMapper.readTree(jsonValue);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse JSON value for schemaId=" + schemaId + ".", ex);
        }
    }

    private void registerOnce(String schemaId, String jsonSchema) {
        if (registeredSchemas.putIfAbsent(schemaId, Boolean.TRUE) != null) {
            return;
        }
        schemaRegistryClient.register(schemaId, jsonSchema);
    }
}


