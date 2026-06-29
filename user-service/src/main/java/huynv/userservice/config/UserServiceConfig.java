package huynv.userservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import huynv.event.EventFactory;
import huynv.event.schema.ClasspathSchemaLoader;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.schema.NoopSchemaRegistryClient;
import huynv.event.schema.SchemaRegistryClient;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.outbox.KafkaOutboxRepository;
import huynv.eventinfra.outbox.KafkaOutboxService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Objects;

/**
 * Registers user-service infrastructure beans required for caching and transactional outbox publishing.
 */
@Configuration
@EnableConfigurationProperties(UserServiceProperties.class)
public class UserServiceConfig {

    /**
     * Provides an ObjectMapper configured for stable JSON payload serialization.
     *
     * @return Returns an ObjectMapper configured with module discovery and tolerant deserialization.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Adapts user-service outbox settings into the shared NotificationProperties contract required by shared outbox infrastructure.
     *
     * @param properties User-service properties containing DLQ and retry settings.
     * @return Returns a NotificationProperties instance populated with user-service outbox settings.
     */
    @Bean
    public NotificationProperties notificationProperties(UserServiceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        NotificationProperties notificationProperties = new NotificationProperties();
        notificationProperties.getKafka().setDlqTopic(properties.getKafka().getDlqTopic());
        notificationProperties.getRetry().setMaxAttempts(properties.getOutbox().getMaxAttempts());
        notificationProperties.getRetry().setInitialIntervalMs(properties.getOutbox().getInitialBackoff().toMillis());
        notificationProperties.getRetry().setMultiplier(properties.getOutbox().getBackoffMultiplier());
        notificationProperties.getRetry().setMaxIntervalMs(properties.getOutbox().getMaxBackoff().toMillis());
        notificationProperties.getOutbox().getPublisher().setBatchSize(properties.getOutbox().getBatchSize());
        notificationProperties.getOutbox().getPublisher().setFixedDelayMs(properties.getOutbox().getFixedDelay().toMillis());
        notificationProperties.getOutbox().getPublisher().setSendTimeoutMs(properties.getOutbox().getSendTimeout().toMillis());
        notificationProperties.getOutbox().getPublisher().setProcessingTimeoutMs(properties.getOutbox().getProcessingTimeout().toMillis());
        return notificationProperties;
    }

    /**
     * Creates an event factory that stamps user-service as the publishing source.
     *
     * @param tracerProvider Optional Micrometer tracer provider used to propagate active trace identifiers.
     * @return Returns an event factory configured for user-service envelopes.
     */
    @Bean
    public EventFactory eventFactory(ObjectProvider<Tracer> tracerProvider) {
        Objects.requireNonNull(tracerProvider, "tracerProvider");
        return new EventFactory("user-service", () -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    return currentSpan.context().traceId();
                }
            }
            return MDC.get("traceId");
        });
    }

    /**
     * Creates the shared classpath schema loader for event-contract schemas.
     *
     * @return Returns a schema loader rooted at the shared schema directory.
     */
    @Bean
    public ClasspathSchemaLoader classpathSchemaLoader() {
        return new ClasspathSchemaLoader();
    }

    /**
     * Creates a no-op schema registry client for environments without live schema registration.
     *
     * @return Returns a no-op schema registry client.
     */
    @Bean
    public SchemaRegistryClient schemaRegistryClient() {
        return new NoopSchemaRegistryClient();
    }

    /**
     * Creates the shared JSON Schema validation service used before publishing events.
     *
     * @param objectMapper Object mapper used to parse event payloads and schemas.
     * @param classpathSchemaLoader Shared classpath schema loader.
     * @param schemaRegistryClient Schema registry client used for optional registration.
     * @return Returns a shared JSON Schema validation service.
     */
    @Bean
    public JsonSchemaValidationService jsonSchemaValidationService(
            ObjectMapper objectMapper,
            ClasspathSchemaLoader classpathSchemaLoader,
            SchemaRegistryClient schemaRegistryClient
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(classpathSchemaLoader, "classpathSchemaLoader");
        Objects.requireNonNull(schemaRegistryClient, "schemaRegistryClient");
        return new JsonSchemaValidationService(objectMapper, classpathSchemaLoader, schemaRegistryClient);
    }

    /**
     * Creates the shared transactional outbox service backed by the service database.
     *
     * @param notificationProperties Adapted outbox retry and DLQ configuration.
     * @param repository Shared Kafka outbox repository.
     * @param objectMapper ObjectMapper used to serialize persisted outbox headers.
     * @return Returns a KafkaOutboxService that persists outbox rows transactionally.
     */
    @Bean
    public KafkaOutboxService kafkaOutboxService(
            NotificationProperties notificationProperties,
            KafkaOutboxRepository repository,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(notificationProperties, "notificationProperties");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(objectMapper, "objectMapper");
        return new KafkaOutboxService(notificationProperties, repository, objectMapper);
    }
}

