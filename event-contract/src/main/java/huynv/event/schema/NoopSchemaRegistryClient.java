package huynv.event.schema;

import java.util.Objects;

/**
 * Provides a no-op schema registry client for deployments without a registry.
 */
public final class NoopSchemaRegistryClient implements SchemaRegistryClient {

    /**
     * Creates a no-op schema registry client.
     *
     * @return Initializes a no-op client instance.
     */
    public NoopSchemaRegistryClient() {
    }

    @Override
    public void register(String schemaId, String jsonSchema) {
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
    }
}


