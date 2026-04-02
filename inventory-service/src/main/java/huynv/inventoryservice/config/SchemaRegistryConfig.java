package huynv.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.schema.ApicurioRegistryClient;
import huynv.event.schema.ClasspathSchemaLoader;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.schema.NoopSchemaRegistryClient;
import huynv.event.schema.SchemaRegistryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * Declares JSON Schema validation and schema registry registration components for inventory events.
 */
@Configuration
public class SchemaRegistryConfig {

    /**
     * Creates a classpath schema loader using the default {@code schemas/} base directory.
     *
     * @return Returns a schema loader that resolves schema resources by schema id.
     */
    @Bean
    public ClasspathSchemaLoader classpathSchemaLoader() {
        return new ClasspathSchemaLoader();
    }

    /**
     * Creates a schema registry client backed by Apicurio when configured, otherwise a no-op client.
     *
     * @param schemaRegistryUrl Schema registry URL used for schema registration, or blank to disable.
     * @return Returns a schema registry client implementation.
     */
    @Bean
    public SchemaRegistryClient schemaRegistryClient(@Value("${schema.registry.url:}") String schemaRegistryUrl) {
        if (schemaRegistryUrl == null || schemaRegistryUrl.isBlank()) {
            return new NoopSchemaRegistryClient();
        }
        return new ApicurioRegistryClient(URI.create(schemaRegistryUrl), "default", Duration.ofSeconds(3));
    }

    /**
     * Creates a JSON Schema validation service used by the outbox to validate and register event schemas.
     *
     * @param objectMapper ObjectMapper used to parse JSON values and schema documents.
     * @param schemaLoader Schema loader used to resolve schema documents from classpath.
     * @param schemaRegistryClient Schema registry client used for optional schema registration.
     * @return Returns a JSON Schema validation service.
     */
    @Bean
    public JsonSchemaValidationService jsonSchemaValidationService(
            ObjectMapper objectMapper,
            ClasspathSchemaLoader schemaLoader,
            SchemaRegistryClient schemaRegistryClient
    ) {
        return new JsonSchemaValidationService(objectMapper, schemaLoader, schemaRegistryClient);
    }
}


