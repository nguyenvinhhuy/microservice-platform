package huynv.auditlogservice.api;

import huynv.auditlogservice.domain.AuditLog;
import huynv.auditlogservice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

  @Mock private AuditLogRepository repository;
  private AuditLogController controller;

  private static final Long TENANT_ID = 1L;
  private static final Long OTHER_TENANT_ID = 99L;
  private static final Long USER_ID = 42L;

  /**
   * Initializes the controller under test with a mock repository before each test.
   *
   * @return Resets the controller instance to a clean state backed by a fresh mock repository.
   */
  @BeforeEach
  void setUp() {
    controller = new AuditLogController(repository);
  }

  // -----------------------------------------------------------------------
  // listAuditLogs
  // -----------------------------------------------------------------------

  /**
   * Verifies that listing audit logs without filters delegates to the repository's tenant-scoped query.
   *
   * @return Asserts that findByTenantId is called with the correct tenant identifier and pageable parameters.
   */
  @Test
  void listAuditLogs_noFilter_callsFindByTenantId() {
    when(repository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
        .thenReturn(Page.empty());

    controller.listAuditLogs(TENANT_ID, null, null, 0, 50);

    verify(repository).findByTenantId(eq(TENANT_ID), any(Pageable.class));
  }

  /**
   * Verifies that listing audit logs with an eventType filter delegates to the event-type-scoped repository query.
   *
   * @return Asserts that findByTenantIdAndEventType is invoked with the matching tenant and event type values.
   */
  @Test
  void listAuditLogs_withEventType_callsFindByTenantIdAndEventType() {
    when(repository.findByTenantIdAndEventType(eq(TENANT_ID), eq("order.created"), any(Pageable.class)))
        .thenReturn(Page.empty());

    controller.listAuditLogs(TENANT_ID, "order.created", null, 0, 50);

    verify(repository).findByTenantIdAndEventType(eq(TENANT_ID), eq("order.created"), any(Pageable.class));
  }

  /**
   * Verifies that listing audit logs with an aggregateId filter delegates to the aggregate-scoped repository query.
   *
   * @return Asserts that findByTenantIdAndAggregateId is invoked with the correct tenant and aggregate identifier.
   */
  @Test
  void listAuditLogs_withAggregateId_callsFindByTenantIdAndAggregateId() {
    when(repository.findByTenantIdAndAggregateId(eq(TENANT_ID), eq("order-123"), any(Pageable.class)))
        .thenReturn(Page.empty());

    controller.listAuditLogs(TENANT_ID, null, "order-123", 0, 50);

    verify(repository).findByTenantIdAndAggregateId(eq(TENANT_ID), eq("order-123"), any(Pageable.class));
  }

  /**
   * Verifies that the listing response correctly maps each AuditLog entity to an AuditLogResponse record.
   *
   * @return Asserts that the response page contains exactly one entry with the expected eventId, eventType, tenantId, and userId.
   */
  @Test
  void listAuditLogs_mapsEntityToResponse() {
    AuditLog log = buildLog(TENANT_ID, USER_ID, "order.created");
    when(repository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(log)));

    Page<AuditLogResponse> result = controller.listAuditLogs(TENANT_ID, null, null, 0, 50);

    assertThat(result.getTotalElements()).isEqualTo(1);
    AuditLogResponse response = result.getContent().get(0);
    assertThat(response.eventId()).isEqualTo("evt-001");
    assertThat(response.eventType()).isEqualTo("order.created");
    assertThat(response.tenantId()).isEqualTo(TENANT_ID);
    assertThat(response.userId()).isEqualTo(USER_ID);
  }

  // -----------------------------------------------------------------------
  // getById
  // -----------------------------------------------------------------------

  /**
   * Verifies that retrieving an existing audit log entry whose tenantId matches returns HTTP 200 with the log body.
   *
   * @return Asserts that the response status is 200 and the body contains a non-null response with the expected eventId.
   */
  @Test
  void getById_found_sameTenant_returns200() {
    AuditLog log = buildLog(TENANT_ID, USER_ID, "order.created");
    when(repository.findById(1L)).thenReturn(Optional.of(log));

    ResponseEntity<AuditLogResponse> response = controller.getById(TENANT_ID, 1L);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().eventId()).isEqualTo("evt-001");
  }

  /**
   * Verifies that retrieving an audit log entry with a tenant ID that does not match the record's tenant returns HTTP 404.
   *
   * @return Asserts that the response status is 404 when the caller's tenantId differs from the record's tenantId.
   */
  @Test
  void getById_wrongTenant_returns404() {
    AuditLog log = buildLog(TENANT_ID, USER_ID, "order.created");
    when(repository.findById(1L)).thenReturn(Optional.of(log));

    ResponseEntity<AuditLogResponse> response = controller.getById(OTHER_TENANT_ID, 1L);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  /**
   * Verifies that retrieving a non-existent audit log entry returns HTTP 404.
   *
   * @return Asserts that the response status is 404 when the repository returns an empty Optional.
   */
  @Test
  void getById_notFound_returns404() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    ResponseEntity<AuditLogResponse> response = controller.getById(TENANT_ID, 999L);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  // -----------------------------------------------------------------------
  // getByUser
  // -----------------------------------------------------------------------

  /**
   * Verifies that retrieving audit logs for a specific user returns a paginated result scoped to that user's tenant.
   *
   * @return Asserts that the page contains exactly one entry with the expected userId and that the correct repository method is invoked.
   */
  @Test
  void getByUser_returnsPaginatedResult() {
    AuditLog log = buildLog(TENANT_ID, USER_ID, "user.updated");
    when(repository.findByTenantIdAndUserId(eq(TENANT_ID), eq(USER_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(log)));

    Page<AuditLogResponse> result = controller.getByUser(TENANT_ID, USER_ID, 0, 50);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).userId()).isEqualTo(USER_ID);
    verify(repository).findByTenantIdAndUserId(eq(TENANT_ID), eq(USER_ID), any(Pageable.class));
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Constructs a fully populated AuditLog entity with the given tenant, user, and event type for use in tests.
   *
   * @param tenantId  The tenant identifier to assign to the log entry.
   * @param userId    The user identifier to assign to the log entry.
   * @param eventType The event type string to assign to the log entry.
   * @return A new AuditLog instance with pre-populated fields and a fixed eventId of "evt-001".
   */
  private static AuditLog buildLog(Long tenantId, Long userId, String eventType) {
    AuditLog log = new AuditLog();
    log.setEventId("evt-001");
    log.setEventType(eventType);
    log.setSource("order-service");
    log.setTenantId(tenantId);
    log.setUserId(userId);
    log.setAggregateId("agg-001");
    log.setAggregateType("order");
    log.setCorrelationId("corr-001");
    log.setCausationId("caus-001");
    log.setRawPayload("{}");
    log.setReceivedAt(OffsetDateTime.now());
    return log;
  }
}
