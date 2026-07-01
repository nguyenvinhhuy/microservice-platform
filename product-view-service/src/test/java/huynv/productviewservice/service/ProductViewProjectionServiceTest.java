package huynv.productviewservice.service;

import huynv.productviewservice.model.ProductView;
import huynv.productviewservice.model.ProductViewId;
import huynv.productviewservice.repository.ProductViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductViewProjectionService — verifies upsert and stock-update operations
 * on the product read-model projection, covering new-row creation, in-place updates, and
 * field preservation rules such as keeping an existing name when a blank value is supplied.
 */
@ExtendWith(MockitoExtension.class)
class ProductViewProjectionServiceTest {

  @Mock private ProductViewRepository productViewRepository;

  private ProductViewProjectionService service;

  private static final Long TENANT_ID = 1L;
  private static final Long PRODUCT_ID = 10L;

  /**
   * Initialises the ProductViewProjectionService with the mocked repository and registers a
   * lenient stub so that save() returns the passed argument for all non-throwing tests.
   *
   * @return Wires the service and registers the save stub before each test.
   */
  @BeforeEach
  void setUp() {
    service = new ProductViewProjectionService(productViewRepository);
    // Lenient: validation tests throw before save() is reached.
    lenient().when(productViewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  // -----------------------------------------------------------------------
  // upsertProduct
  // -----------------------------------------------------------------------

  /**
   * Verifies that upsertProduct persists a new ProductView row with name, price, status, and
   * updatedAt populated when no existing record is found for the given tenant and product ID.
   *
   * @return Asserts name, price, status, and updatedAt on the saved ProductView.
   */
  @Test
  void upsertProduct_newRow_persistsAllFields() {
    when(productViewRepository.findById(any(ProductViewId.class))).thenReturn(Optional.empty());

    service.upsertProduct(TENANT_ID, PRODUCT_ID, "Widget", BigDecimal.valueOf(49.99), "ACTIVE", null);

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    ProductView saved = captor.getValue();

    assertThat(saved.getName()).isEqualTo("Widget");
    assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  /**
   * Verifies that upsertProduct updates an existing ProductView row in place with the new name,
   * price, and status values rather than creating a duplicate record.
   *
   * @return Asserts the saved ProductView has name "New Name" and status "INACTIVE".
   */
  @Test
  void upsertProduct_existingRow_updatesFieldsInPlace() {
    ProductView existing = new ProductView();
    existing.setId(new ProductViewId(TENANT_ID, PRODUCT_ID));
    existing.setName("Old Name");
    existing.setPrice(BigDecimal.TEN);
    when(productViewRepository.findById(any())).thenReturn(Optional.of(existing));

    service.upsertProduct(TENANT_ID, PRODUCT_ID, "New Name", BigDecimal.valueOf(99.00), "INACTIVE", null);

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("New Name");
    assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
  }

  /**
   * Verifies that upsertProduct preserves the existing product name when a blank name is supplied,
   * preventing accidental erasure of the display name by partial-update events.
   *
   * @return Asserts the saved ProductView retains name "Existing Name" when a blank name is passed.
   */
  @Test
  void upsertProduct_blankName_doesNotOverwriteExistingName() {
    ProductView existing = new ProductView();
    existing.setId(new ProductViewId(TENANT_ID, PRODUCT_ID));
    existing.setName("Existing Name");
    when(productViewRepository.findById(any())).thenReturn(Optional.of(existing));

    service.upsertProduct(TENANT_ID, PRODUCT_ID, "  ", null, null, null);

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("Existing Name");
  }

  /**
   * Verifies that upsertProduct throws a NullPointerException when tenantId is null,
   * enforcing that a valid tenant identifier is required to build the composite primary key.
   *
   * @return Asserts that NullPointerException is thrown when tenantId argument is null.
   */
  @Test
  void upsertProduct_nullTenantId_throwsNullPointerException() {
    assertThatThrownBy(
            () ->
                service.upsertProduct(
                    null, PRODUCT_ID, "Name", BigDecimal.TEN, "ACTIVE", OffsetDateTime.now()))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // updateStock
  // -----------------------------------------------------------------------

  /**
   * Verifies that updateStock sets the stock quantity and derives "IN_STOCK" status when
   * a positive stock value is provided, creating a new row if none exists.
   *
   * @return Asserts the saved ProductView has stock 5 and status "IN_STOCK".
   */
  @Test
  void updateStock_positiveStock_setsInStockStatus() {
    when(productViewRepository.findById(any())).thenReturn(Optional.empty());

    service.updateStock(TENANT_ID, PRODUCT_ID, 5, OffsetDateTime.now());

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStock()).isEqualTo(5);
    assertThat(captor.getValue().getStatus()).isEqualTo("IN_STOCK");
  }

  /**
   * Verifies that updateStock sets the stock quantity to 0 and derives "OUT_OF_STOCK" status,
   * ensuring the read model reflects unavailability when inventory is fully depleted.
   *
   * @return Asserts the saved ProductView has stock 0 and status "OUT_OF_STOCK".
   */
  @Test
  void updateStock_zeroStock_setsOutOfStockStatus() {
    when(productViewRepository.findById(any())).thenReturn(Optional.empty());

    service.updateStock(TENANT_ID, PRODUCT_ID, 0, OffsetDateTime.now());

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStock()).isEqualTo(0);
    assertThat(captor.getValue().getStatus()).isEqualTo("OUT_OF_STOCK");
  }

  /**
   * Verifies that updateStock leaves the existing status unchanged when a null stock value is
   * supplied, preventing accidental overwrite of the status by incomplete stock events.
   *
   * @return Asserts the saved ProductView retains status "IN_STOCK" when null stock is passed.
   */
  @Test
  void updateStock_nullStock_skipsStatusChange() {
    ProductView existing = new ProductView();
    existing.setStatus("IN_STOCK");
    when(productViewRepository.findById(any())).thenReturn(Optional.of(existing));

    service.updateStock(TENANT_ID, PRODUCT_ID, null, OffsetDateTime.now());

    ArgumentCaptor<ProductView> captor = ArgumentCaptor.forClass(ProductView.class);
    verify(productViewRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("IN_STOCK"); // unchanged
  }
}
