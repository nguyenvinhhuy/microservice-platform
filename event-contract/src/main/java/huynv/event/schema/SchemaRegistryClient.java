package huynv.event.schema;

/**
 * Registers JSON Schemas for event types in an external schema registry.
 */
public interface SchemaRegistryClient {

    /**
     * Registers the given JSON Schema content for the provided schema identifier.
     *
     * @param schemaId Stable schema identifier including version suffix.
     * @param jsonSchema JSON Schema document used to validate events.
     * @return Performs a side effect by registering or updating the schema in the registry.
     */
    void register(String schemaId, String jsonSchema);
}


