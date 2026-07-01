package huynv.auditlogservice.service;

import huynv.auditlogservice.domain.AuditLog;
import huynv.auditlogservice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuditLogService verifying persistence and validation behaviour.
 */
class AuditLogServiceTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    /**
     * Initializes the service under test with a mock repository and configures the save stub before each test.
     *
     * @return Resets the service to a clean state backed by a fresh mock repository that echoes back saved entities.
     */
    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Verifies that record persists an AuditLog entity with all expected field values populated correctly.
     *
     * @return Asserts that every field of the captured AuditLog matches the corresponding argument passed to record.
     */
    @Test
    void record_persistsAuditLogWithCorrectFields() {
        service.record(
                "evt-123",
                "order.created",
                "order-service",
                42L,
                7L,
                "agg-456",
                "corr-789",
                "cause-001",
                "{\"eventId\":\"evt-123\"}"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-123");
        assertThat(saved.getEventType()).isEqualTo("order.created");
        assertThat(saved.getSource()).isEqualTo("order-service");
        assertThat(saved.getTenantId()).isEqualTo(42L);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getAggregateId()).isEqualTo("agg-456");
        assertThat(saved.getAggregateType()).isEqualTo("order");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-789");
        assertThat(saved.getCausationId()).isEqualTo("cause-001");
        assertThat(saved.getRawPayload()).isEqualTo("{\"eventId\":\"evt-123\"}");
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    /**
     * Verifies that record derives the aggregate type by extracting the prefix before the first dot in the event type string.
     *
     * @return Asserts that the aggregateType field of the saved AuditLog equals the first dot-separated segment of the event type.
     */
    @Test
    void record_derivesAggregateTypeFromEventTypePrefix() {
        service.record("e1", "payment.completed", null, null, null, null, null, null, "{}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("payment");
    }

    /**
     * Verifies that record rejects a null eventId by throwing a NullPointerException.
     *
     * @return Asserts that a NullPointerException is thrown when null is supplied as the eventId argument.
     */
    @Test
    void record_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() ->
                service.record(null, "order.created", null, null, null, null, null, null, "{}"))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that record rejects a null rawPayload by throwing a NullPointerException.
     *
     * @return Asserts that a NullPointerException is thrown when null is supplied as the rawPayload argument.
     */
    @Test
    void record_throwsWhenRawPayloadIsNull() {
        assertThatThrownBy(() ->
                service.record("e1", "order.created", null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that record rejects a null eventType by throwing a NullPointerException.
     *
     * @return Asserts that a NullPointerException is thrown when null is supplied as the eventType argument.
     */
    @Test
    void record_throwsWhenEventTypeIsNull() {
        assertThatThrownBy(() ->
                service.record("e1", null, null, null, null, null, null, null, "{}"))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that record uses the full event type string as the aggregate type when the event type contains no dot separator.
     *
     * @return Asserts that the aggregateType field of the saved AuditLog equals the entire event type when no dot is present.
     */
    @Test
    void record_derivesAggregateType_noDot_returnsFullEventType() {
        service.record("e2", "inventory", null, null, null, null, null, null, "{}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("inventory");
    }

    /**
     * Verifies that constructing AuditLogService with a null repository throws a NullPointerException.
     *
     * @return Asserts that a NullPointerException is thrown at construction time when the repository argument is null.
     */
    @Test
    void constructor_nullRepository_throwsNullPointerException() {
        assertThatThrownBy(() -> new AuditLogService(null))
                .isInstanceOf(NullPointerException.class);
    }
}

