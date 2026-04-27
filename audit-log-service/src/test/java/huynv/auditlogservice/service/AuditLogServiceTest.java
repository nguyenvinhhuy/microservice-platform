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
     * Initializes the service with a mock repository before each test.
     *
     * @return Performs a side effect by resetting mocks and creating a fresh service instance.
     */
    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Verifies that record persists an AuditLog entity with the expected field values.
     *
     * @return Performs a side effect by asserting captured entity fields match the input parameters.
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
     * Verifies that deriveAggregateType correctly extracts the aggregate type from a compound event type.
     *
     * @return Performs a side effect by asserting the aggregate type field equals the first event type segment.
     */
    @Test
    void record_derivesAggregateTypeFromEventTypePrefix() {
        service.record("e1", "payment.completed", null, null, null, null, null, null, "{}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("payment");
    }

    /**
     * Verifies that record throws NullPointerException when eventId is null.
     *
     * @return Performs a side effect by asserting a NullPointerException is thrown for a null eventId.
     */
    @Test
    void record_throwsWhenEventIdIsNull() {
        assertThatThrownBy(() ->
                service.record(null, "order.created", null, null, null, null, null, null, "{}"))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies that record throws NullPointerException when rawPayload is null.
     *
     * @return Performs a side effect by asserting a NullPointerException is thrown for a null rawPayload.
     */
    @Test
    void record_throwsWhenRawPayloadIsNull() {
        assertThatThrownBy(() ->
                service.record("e1", "order.created", null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}

