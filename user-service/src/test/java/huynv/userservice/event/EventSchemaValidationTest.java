package huynv.userservice.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.event.schema.ClasspathSchemaLoader;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.schema.NoopSchemaRegistryClient;
import huynv.event.user.UserCreatedEvent;
import huynv.event.user.UserEventTypes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies user-service event envelopes against the shared JSON Schema contract and trace propagation rules.
 */
class EventSchemaValidationTest {

    /**
     * Validates that a canonical user event contains the shared schema identifier and propagated trace identifier.
     *
     * @return Verifies that the shared schema validator accepts the envelope and that trace propagation is preserved.
     */
    @Test
    void validatesCanonicalUserEventAgainstSharedSchema() {
        ObjectMapper objectMapper = objectMapper();
        JsonSchemaValidationService jsonSchemaValidationService = new JsonSchemaValidationService(
                objectMapper,
                new ClasspathSchemaLoader(),
                new NoopSchemaRegistryClient()
        );
        UserEventSchemaValidator validator = new UserEventSchemaValidator(objectMapper, jsonSchemaValidationService);
        EventFactory eventFactory = new EventFactory(
                "user-service",
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC),
                () -> "trace-id-123"
        );
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID keycloakUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UserCreatedEvent payload = new UserCreatedEvent(
                userId,
                keycloakUserId,
                tenantId,
                "user@example.com",
                "Platform User",
                "+123456789",
                null,
                "ACTIVE",
                "en-US",
                "UTC",
                Instant.parse("2026-05-01T12:00:00Z")
        );

        BaseEvent<UserCreatedEvent> event = eventFactory.create(
                UserEventTypes.USER_CREATED_V1,
                userId.toString(),
                1L,
                UserEventTypes.USER_CREATED_V1,
                "corr-1",
                "cause-1",
                payload
        );

        validator.validate(event);

        assertThat(event.dataSchema()).isEqualTo(UserEventTypes.USER_CREATED_V1);
        assertThat(event.traceId()).isEqualTo("trace-id-123");
    }

    /**
     * Rejects envelopes whose declared schema identifier no longer matches the event type.
     *
     * @return Verifies that mismatched event metadata is rejected before outbox enqueueing.
     */
    @Test
    void rejectsMismatchedDataSchema() {
        ObjectMapper objectMapper = objectMapper();
        JsonSchemaValidationService jsonSchemaValidationService = new JsonSchemaValidationService(
                objectMapper,
                new ClasspathSchemaLoader(),
                new NoopSchemaRegistryClient()
        );
        UserEventSchemaValidator validator = new UserEventSchemaValidator(objectMapper, jsonSchemaValidationService);
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID keycloakUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        BaseEvent<UserCreatedEvent> event = new BaseEvent<>(
                "01JTESTEVENT1234567890ABCDEF",
                UserEventTypes.USER_CREATED_V1,
                "user-service",
                Instant.parse("2026-05-01T12:00:00Z"),
                userId.toString(),
                1L,
                "schemas/user.created.v1.json",
                "trace-id-123",
                "corr-1",
                "cause-1",
                new UserCreatedEvent(
                        userId,
                        keycloakUserId,
                        tenantId,
                        "user@example.com",
                        "Platform User",
                        "+123456789",
                        null,
                        "ACTIVE",
                        "en-US",
                        "UTC",
                        Instant.parse("2026-05-01T12:00:00Z")
                )
        );

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event dataSchema must exactly match eventType.");
    }

    /**
     * Creates an object mapper configured like the service runtime mapper for schema tests.
     *
     * @return Returns an ObjectMapper with module discovery and tolerant deserialization enabled.
     */
    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }
}

