package huynv.orderviewservice.service;

import huynv.orderviewservice.model.OrderView;
import huynv.orderviewservice.model.OrderViewId;
import huynv.orderviewservice.repository.OrderViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderViewProjectionService — verifies upsert and status-update operations
 * on the order read-model projection, covering new-row creation and in-place update paths.
 */
@ExtendWith(MockitoExtension.class)
class OrderViewProjectionServiceTest {

  @Mock private OrderViewRepository orderViewRepository;

  private OrderViewProjectionService service;

  private static final Long TENANT_ID = 1L;
  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final Long USER_ID = 100L;

  /**
   * Initialises the OrderViewProjectionService with the mocked repository and configures a
   * lenient stub so that save() returns the passed argument for all tests.
   *
   * @return Wires the service and registers the save stub before each test.
   */
  @BeforeEach
  void setUp() {
    service = new OrderViewProjectionService(orderViewRepository);
    lenient().when(orderViewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  // -----------------------------------------------------------------------
  // upsertCreated
  // -----------------------------------------------------------------------

  /**
   * Verifies that upsertCreated persists a new OrderView row with all fields populated
   * when no existing record is found for the given tenant and order ID.
   *
   * @return Asserts userId, status, totalPrice, createdAt, and updatedAt on the saved OrderView.
   */
  @Test
  void upsertCreated_newOrder_persistsAllFields() {
    when(orderViewRepository.findById(any(OrderViewId.class))).thenReturn(Optional.empty());

    service.upsertCreated(
        TENANT_ID, ORDER_ID, USER_ID, "CREATED", BigDecimal.valueOf(200.00), OffsetDateTime.now());

    ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
    verify(orderViewRepository).save(captor.capture());
    OrderView saved = captor.getValue();

    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getStatus()).isEqualTo("CREATED");
    assertThat(saved.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  /**
   * Verifies that upsertCreated throws a NullPointerException when orderId is null,
   * enforcing that a valid order identifier is required to build the composite primary key.
   *
   * @return Asserts that NullPointerException is thrown when orderId argument is null.
   */
  @Test
  void upsertCreated_nullOrderId_throwsNullPointerException() {
    assertThatThrownBy(
            () ->
                service.upsertCreated(
                    TENANT_ID, null, USER_ID, "CREATED", BigDecimal.TEN, OffsetDateTime.now()))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that upsertCreated throws a NullPointerException when userId is null,
   * enforcing that a valid user identifier is required to associate the order view with an owner.
   *
   * @return Asserts that NullPointerException is thrown when userId argument is null.
   */
  @Test
  void upsertCreated_nullUserId_throwsNullPointerException() {
    assertThatThrownBy(
            () ->
                service.upsertCreated(
                    TENANT_ID, ORDER_ID, null, "CREATED", BigDecimal.TEN, OffsetDateTime.now()))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // updateOrderStatus
  // -----------------------------------------------------------------------

  /**
   * Verifies that updateOrderStatus updates the status field and refreshes updatedAt on an
   * existing OrderView row without creating a new record.
   *
   * @return Asserts the saved OrderView has status "CONFIRMED" and a non-null updatedAt.
   */
  @Test
  void updateOrderStatus_existingRow_updatesStatusField() {
    OrderView existing = existingView();
    existing.setStatus("CREATED");
    when(orderViewRepository.findById(any())).thenReturn(Optional.of(existing));

    service.updateOrderStatus(TENANT_ID, ORDER_ID, "CONFIRMED");

    ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
    verify(orderViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
    assertThat(captor.getValue().getUpdatedAt()).isNotNull();
  }

  /**
   * Verifies that updateOrderStatus creates a shell OrderView record when no existing row is found,
   * ensuring out-of-order event delivery does not result in lost status updates.
   *
   * @return Asserts the saved OrderView has status "FAILED" even when the row did not exist before.
   */
  @Test
  void updateOrderStatus_noExistingRow_createsShellAndUpdates() {
    when(orderViewRepository.findById(any())).thenReturn(Optional.empty());

    service.updateOrderStatus(TENANT_ID, ORDER_ID, "FAILED");

    ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
    verify(orderViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
  }

  // -----------------------------------------------------------------------
  // updatePaymentStatus
  // -----------------------------------------------------------------------

  /**
   * Verifies that updatePaymentStatus sets the paymentStatus field on an existing OrderView row,
   * reflecting the latest payment outcome in the read model.
   *
   * @return Asserts the saved OrderView has paymentStatus "SUCCEEDED".
   */
  @Test
  void updatePaymentStatus_existingRow_updatesPaymentStatusField() {
    when(orderViewRepository.findById(any())).thenReturn(Optional.of(existingView()));

    service.updatePaymentStatus(TENANT_ID, ORDER_ID, "SUCCEEDED");

    ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
    verify(orderViewRepository).save(captor.capture());
    assertThat(captor.getValue().getPaymentStatus()).isEqualTo("SUCCEEDED");
  }

  // -----------------------------------------------------------------------
  // updateStockStatus
  // -----------------------------------------------------------------------

  /**
   * Verifies that updateStockStatus sets the stockStatus field on an existing OrderView row,
   * reflecting the latest inventory reservation outcome in the read model.
   *
   * @return Asserts the saved OrderView has stockStatus "RESERVED".
   */
  @Test
  void updateStockStatus_existingRow_updatesStockStatusField() {
    when(orderViewRepository.findById(any())).thenReturn(Optional.of(existingView()));

    service.updateStockStatus(TENANT_ID, ORDER_ID, "RESERVED");

    ArgumentCaptor<OrderView> captor = ArgumentCaptor.forClass(OrderView.class);
    verify(orderViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStockStatus()).isEqualTo("RESERVED");
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Builds a minimal OrderView with a composite ID and userId set to the test constants,
   * suitable for use as an existing-record stub return value.
   *
   * @return An OrderView instance pre-populated with TENANT_ID, ORDER_ID, and USER_ID.
   */
  private OrderView existingView() {
    OrderView view = new OrderView();
    view.setId(new OrderViewId(TENANT_ID, ORDER_ID));
    view.setUserId(USER_ID);
    return view;
  }
}
