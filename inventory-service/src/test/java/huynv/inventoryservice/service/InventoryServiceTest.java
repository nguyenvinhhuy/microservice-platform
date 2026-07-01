package huynv.inventoryservice.service;

import huynv.inventoryservice.config.InventoryProperties;
import huynv.inventoryservice.domain.Inventory;
import huynv.inventoryservice.domain.InventoryReservation;
import huynv.inventoryservice.domain.InventoryReservationItem;
import huynv.inventoryservice.domain.ReservationStatus;
import huynv.inventoryservice.dto.ReservationItem;
import huynv.inventoryservice.dto.ReserveStockRequest;
import huynv.inventoryservice.exception.ConcurrentStockUpdateException;
import huynv.inventoryservice.exception.InsufficientStockException;
import huynv.inventoryservice.exception.InvalidReservationStatusException;
import huynv.inventoryservice.exception.ReservationNotFoundException;
import huynv.inventoryservice.exception.TenantOwnershipViolationException;
import huynv.inventoryservice.repository.InventoryRepository;
import huynv.inventoryservice.repository.InventoryReservationRepository;
import huynv.inventoryservice.security.UserContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private InventoryReservationRepository reservationRepository;
  @Mock private InventoryOutboxService inventoryOutboxService;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InventoryProperties inventoryProperties = new InventoryProperties();

  private InventoryService service;

  // Lenient: some tests (e.g. releaseExpiredReservations with empty result) never enter
  // the loop body and therefore never call UserContext — safe to skip for those.
  private MockedStatic<UserContext> userContextMock;

  private static final Long TENANT_ID = 1L;

  /**
   * Initialises a fresh {@link InventoryService} instance and opens a lenient static mock for
   * {@link UserContext} so that tenant and user identity are available throughout each test.
   *
   * @return void — configures shared test state before each test method.
   */
  @BeforeEach
  void setUp() {
    service =
        new InventoryService(
            inventoryRepository,
            reservationRepository,
            inventoryProperties,
            meterRegistry,
            inventoryOutboxService);

    userContextMock =
        Mockito.mockStatic(UserContext.class, Mockito.withSettings().strictness(Strictness.LENIENT));
    userContextMock.when(UserContext::getTenantId).thenReturn(TENANT_ID);
    userContextMock.when(UserContext::getUserId).thenReturn(100L);
  }

  /**
   * Closes the static mock for {@link UserContext} after each test to prevent mock state leaking
   * into subsequent tests.
   *
   * @return void — tears down static mock resources after each test method.
   */
  @AfterEach
  void tearDown() {
    userContextMock.close();
  }

  // -----------------------------------------------------------------------
  // reserveStock
  // -----------------------------------------------------------------------

  /**
   * Verifies that a valid reserve-stock request with sufficient available inventory persists the
   * reservation, decrements available stock, and enqueues both a stock-reserved and stock-updated
   * outbox event.
   *
   * @return void — asserts repository and outbox interactions for the happy-path reservation flow.
   */
  @Test
  void reserveStock_happyPath_savesReservationAndEnqueuesOutboxEvents() {
    UUID orderId = UUID.randomUUID();
    ReserveStockRequest request = buildRequest(orderId, List.of(item(10L, 2)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());
    Inventory inv = inventory(10L, 5, 0);
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inv));
    when(inventoryRepository.reserveStockIfAvailable(TENANT_ID, 10L, 2)).thenReturn(1);

    service.reserveStock(request);

    verify(reservationRepository).save(any(InventoryReservation.class));
    verify(inventoryOutboxService).enqueueStockReserved(any(), any());
    verify(inventoryOutboxService, atLeastOnce()).enqueueStockUpdated(any(), any(), any());
  }

  /**
   * Verifies that re-submitting a reserve-stock request for an order that already has an existing
   * reservation returns immediately without touching the inventory or saving a duplicate
   * reservation.
   *
   * @return void — asserts that inventory and reservation writes are skipped on idempotent replay.
   */
  @Test
  void reserveStock_idempotent_existingReservation_returnsWithoutSaving() {
    UUID orderId = UUID.randomUUID();
    ReserveStockRequest request = buildRequest(orderId, List.of(item(10L, 2)));

    InventoryReservation existing = reservation(orderId, ReservationStatus.RESERVED);
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(existing));

    service.reserveStock(request);

    verify(inventoryRepository, never()).reserveStockIfAvailable(any(), any(), any());
    verify(reservationRepository, never()).save(any());
  }

  /**
   * Verifies that a reserve-stock request for a product not owned by the current tenant throws
   * {@link TenantOwnershipViolationException} before any stock mutation occurs.
   *
   * @return void — asserts that a tenant-ownership violation is detected and raised correctly.
   */
  @Test
  void reserveStock_tenantOwnershipViolation_throwsException() {
    UUID orderId = UUID.randomUUID();
    ReserveStockRequest request = buildRequest(orderId, List.of(item(99L, 1)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of()); // product 99 not owned by tenant

    assertThatThrownBy(() -> service.reserveStock(request))
        .isInstanceOf(TenantOwnershipViolationException.class);
  }

  /**
   * Verifies that requesting more stock than is currently available raises
   * {@link InsufficientStockException} with the affected product ID in the message.
   *
   * @return void — asserts the exception type and message when available stock cannot satisfy
   *     the requested quantity.
   */
  @Test
  void reserveStock_insufficientStock_throwsInsufficientStockException() {
    UUID orderId = UUID.randomUUID();
    ReserveStockRequest request = buildRequest(orderId, List.of(item(10L, 5)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inventory(10L, 2, 0)));
    when(inventoryRepository.reserveStockIfAvailable(TENANT_ID, 10L, 5)).thenReturn(0);

    assertThatThrownBy(() -> service.reserveStock(request))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("10");
  }

  /**
   * Verifies that an {@link ObjectOptimisticLockingFailureException} thrown during stock
   * reservation is translated into a {@link ConcurrentStockUpdateException} for callers.
   *
   * @return void — asserts that low-level optimistic-lock failures are wrapped in the
   *     domain-level concurrency exception.
   */
  @Test
  void reserveStock_optimisticLockFailure_wrapsToConcurrentStockUpdateException() {
    UUID orderId = UUID.randomUUID();
    ReserveStockRequest request = buildRequest(orderId, List.of(item(10L, 2)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inventory(10L, 5, 0)));
    when(inventoryRepository.reserveStockIfAvailable(TENANT_ID, 10L, 2))
        .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L));

    assertThatThrownBy(() -> service.reserveStock(request))
        .isInstanceOf(ConcurrentStockUpdateException.class);
  }

  // -----------------------------------------------------------------------
  // confirmStock
  // -----------------------------------------------------------------------

  /**
   * Verifies that confirming a RESERVED reservation decrements total stock by the reserved
   * quantity, clears the reserved-stock counter, transitions the reservation to CONFIRMED, and
   * enqueues a stock-confirmed outbox event.
   *
   * @return void — asserts inventory mutations and outbox interactions for the confirm happy path.
   */
  @Test
  void confirmStock_happyPath_decrementsStockAndTransitionsToConfirmed() {
    UUID orderId = UUID.randomUUID();
    InventoryReservation res = reservation(orderId, ReservationStatus.RESERVED);
    res.setItems(List.of(reservationItem(res, 10L, 3)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(res));
    Inventory inv = inventory(10L, 10, 3);
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inv));

    service.confirmStock(orderId);

    assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    assertThat(inv.getTotalStock()).isEqualTo(7);
    assertThat(inv.getReservedStock()).isEqualTo(0);
    verify(inventoryOutboxService).enqueueStockConfirmed(any(), any());
  }

  /**
   * Verifies that calling confirmStock on a reservation already in CONFIRMED status returns
   * silently without emitting a duplicate stock-confirmed event.
   *
   * @return void — asserts that idempotent confirm calls do not trigger additional outbox writes.
   */
  @Test
  void confirmStock_idempotent_alreadyConfirmed_returnsWithoutEmittingEvent() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(reservation(orderId, ReservationStatus.CONFIRMED)));

    service.confirmStock(orderId);

    verify(inventoryOutboxService, never()).enqueueStockConfirmed(any(), any());
  }

  /**
   * Verifies that attempting to confirm a reservation whose status is RELEASED raises
   * {@link InvalidReservationStatusException} with the terminal status name in the message.
   *
   * @return void — asserts that confirming an already-released reservation is rejected with the
   *     correct exception.
   */
  @Test
  void confirmStock_releasedStatus_throwsInvalidReservationStatusException() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(reservation(orderId, ReservationStatus.RELEASED)));

    assertThatThrownBy(() -> service.confirmStock(orderId))
        .isInstanceOf(InvalidReservationStatusException.class)
        .hasMessageContaining("RELEASED");
  }

  /**
   * Verifies that attempting to confirm a reservation that does not exist for the current tenant
   * raises {@link ReservationNotFoundException}.
   *
   * @return void — asserts that a missing reservation causes the correct not-found exception.
   */
  @Test
  void confirmStock_reservationNotFound_throwsReservationNotFoundException() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmStock(orderId))
        .isInstanceOf(ReservationNotFoundException.class);
  }

  // -----------------------------------------------------------------------
  // releaseStock
  // -----------------------------------------------------------------------

  /**
   * Verifies that releasing a RESERVED reservation decrements the reserved-stock counter,
   * transitions the reservation to RELEASED, and enqueues a stock-released outbox event.
   *
   * @return void — asserts inventory mutations and outbox interactions for the release happy path.
   */
  @Test
  void releaseStock_happyPath_decrementsReservedAndTransitionsToReleased() {
    UUID orderId = UUID.randomUUID();
    InventoryReservation res = reservation(orderId, ReservationStatus.RESERVED);
    res.setItems(List.of(reservationItem(res, 10L, 3)));

    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(res));
    Inventory inv = inventory(10L, 10, 3);
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inv));

    service.releaseStock(orderId);

    assertThat(res.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    assertThat(inv.getReservedStock()).isEqualTo(0);
    verify(inventoryOutboxService).enqueueStockReleased(any(), any());
  }

  /**
   * Verifies that calling releaseStock on a reservation already in RELEASED status returns
   * silently without emitting a duplicate stock-released event.
   *
   * @return void — asserts that idempotent release calls do not trigger additional outbox writes.
   */
  @Test
  void releaseStock_idempotent_alreadyReleased_returnsWithoutError() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(reservation(orderId, ReservationStatus.RELEASED)));

    service.releaseStock(orderId);

    verify(inventoryOutboxService, never()).enqueueStockReleased(any(), any());
  }

  /**
   * Verifies that calling releaseStock when the saga has already progressed to CONFIRMED status
   * returns silently, because the stock was consumed and no release event should be emitted.
   *
   * @return void — asserts that attempting to release a confirmed reservation is treated as a
   *     no-op without outbox interaction.
   */
  @Test
  void releaseStock_alreadyConfirmed_returnsWithoutError() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.of(reservation(orderId, ReservationStatus.CONFIRMED)));

    service.releaseStock(orderId); // saga moved on — not an error

    verify(inventoryOutboxService, never()).enqueueStockReleased(any(), any());
  }

  /**
   * Verifies that attempting to release a reservation that does not exist for the current tenant
   * raises {@link ReservationNotFoundException}.
   *
   * @return void — asserts that a missing reservation causes the correct not-found exception on
   *     release.
   */
  @Test
  void releaseStock_reservationNotFound_throwsReservationNotFoundException() {
    UUID orderId = UUID.randomUUID();
    when(reservationRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.releaseStock(orderId))
        .isInstanceOf(ReservationNotFoundException.class);
  }

  // -----------------------------------------------------------------------
  // releaseExpiredReservations
  // -----------------------------------------------------------------------

  /**
   * Verifies that when no reservations have passed their expiry time the scheduled release job
   * completes without interacting with the inventory repository or enqueuing any outbox events.
   *
   * @return void — asserts that an empty expired-reservation set produces no side effects.
   */
  @Test
  void releaseExpiredReservations_noExpired_doesNothing() {
    when(reservationRepository.findByStatusAndExpiresAtBeforeAndTenantIdIsNotNull(
            eq(ReservationStatus.RESERVED), any(OffsetDateTime.class)))
        .thenReturn(List.of());

    service.releaseExpiredReservations();

    verify(inventoryOutboxService, never()).enqueueStockReleased(any(), any());
  }

  /**
   * Verifies that when multiple reservations have expired, each is independently released,
   * transitions to RELEASED status, and enqueues a separate stock-released outbox event.
   *
   * @return void — asserts per-reservation release mutations and outbox event count for a batch
   *     expiry sweep.
   */
  @Test
  void releaseExpiredReservations_multipleExpired_releasesEachAndEnqueuesEvents() {
    UUID orderId1 = UUID.randomUUID();
    UUID orderId2 = UUID.randomUUID();

    InventoryReservation res1 = reservation(orderId1, ReservationStatus.RESERVED);
    res1.setItems(List.of(reservationItem(res1, 10L, 2)));
    InventoryReservation res2 = reservation(orderId2, ReservationStatus.RESERVED);
    res2.setItems(List.of(reservationItem(res2, 20L, 1)));

    when(reservationRepository.findByStatusAndExpiresAtBeforeAndTenantIdIsNotNull(
            eq(ReservationStatus.RESERVED), any(OffsetDateTime.class)))
        .thenReturn(List.of(res1, res2));

    Inventory inv10 = inventory(10L, 5, 2);
    Inventory inv20 = inventory(20L, 3, 1);
    when(inventoryRepository.findAllByProductIdInAndTenantId(anyList(), eq(TENANT_ID)))
        .thenReturn(List.of(inv10))
        .thenReturn(List.of(inv20));

    service.releaseExpiredReservations();

    assertThat(res1.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    assertThat(res2.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    verify(inventoryOutboxService, times(2)).enqueueStockReleased(any(), any());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Builds a {@link ReserveStockRequest} populated with the given order ID and items plus
   * sensible defaults for amount, currency, provider, idempotency key, and trace context.
   *
   * @param orderId the order identifier to embed in the request.
   * @param items the list of product-quantity pairs to reserve.
   * @return a fully populated {@link ReserveStockRequest} ready to pass to the service.
   */
  private ReserveStockRequest buildRequest(UUID orderId, List<ReservationItem> items) {
    ReserveStockRequest req = new ReserveStockRequest();
    req.setOrderId(orderId);
    req.setItems(items);
    req.setAmount(BigDecimal.TEN);
    req.setCurrency("USD");
    req.setPaymentProvider("simulated");
    req.setIdempotencyKey("idem-" + orderId);
    req.setCorrelationId("corr-1");
    req.setTraceId("trace-1");
    return req;
  }

  /**
   * Constructs a {@link ReservationItem} with the specified product ID and quantity for use in
   * test requests.
   *
   * @param productId the identifier of the product to reserve.
   * @param quantity the number of units to reserve.
   * @return a {@link ReservationItem} configured with the given product and quantity.
   */
  private ReservationItem item(Long productId, Integer quantity) {
    ReservationItem ri = new ReservationItem();
    ri.setProductId(productId);
    ri.setQuantity(quantity);
    return ri;
  }

  /**
   * Creates an {@link Inventory} entity for the current tenant with the given product ID, total
   * stock level, and reserved-stock count.
   *
   * @param productId the identifier of the product whose inventory to create.
   * @param total the total number of units in stock.
   * @param reserved the number of units currently reserved.
   * @return a configured {@link Inventory} entity associated with the test tenant.
   */
  private Inventory inventory(Long productId, Integer total, Integer reserved) {
    Inventory inv = new Inventory();
    inv.setProductId(productId);
    inv.setTotalStock(total);
    inv.setReservedStock(reserved);
    inv.setTenantId(TENANT_ID);
    return inv;
  }

  /**
   * Builds an {@link InventoryReservation} for the current tenant with the given order ID and
   * initial status, using a random reservation ID.
   *
   * @param orderId the order ID to associate with the reservation.
   * @param status the initial {@link ReservationStatus} to set on the reservation.
   * @return a configured {@link InventoryReservation} ready for use in tests.
   */
  private InventoryReservation reservation(UUID orderId, ReservationStatus status) {
    return InventoryReservation.builder()
        .reservationId(UUID.randomUUID())
        .orderId(orderId)
        .tenantId(TENANT_ID)
        .status(status)
        .correlationId("corr-1")
        .build();
  }

  /**
   * Creates an {@link InventoryReservationItem} linked to the given reservation, specifying the
   * product and quantity being reserved.
   *
   * @param reservation the parent {@link InventoryReservation} to associate with the item.
   * @param productId the identifier of the reserved product.
   * @param quantity the number of units reserved for the product.
   * @return a configured {@link InventoryReservationItem} linked to the given reservation.
   */
  private InventoryReservationItem reservationItem(
      InventoryReservation reservation, Long productId, Integer quantity) {
    return InventoryReservationItem.builder()
        .reservation(reservation)
        .productId(productId)
        .quantity(quantity)
        .build();
  }
}
