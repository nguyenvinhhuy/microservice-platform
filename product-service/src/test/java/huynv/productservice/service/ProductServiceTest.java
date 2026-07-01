package huynv.productservice.service;

import huynv.productservice.context.UserContext;
import huynv.productservice.dto.ProductDTO;
import huynv.productservice.exception.QuotaExceededException;
import huynv.productservice.model.Product;
import huynv.productservice.model.ProductStatus;
import huynv.productservice.repository.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PlanService planService;
  @Mock private ProductOutboxService productOutboxService;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private ProductService service;

  private static final Long TENANT_ID = 1L;
  private static final Long USER_ID = 100L;

  /**
   * Configures a {@link UserContext} with a known tenant and user, builds the {@link ProductService} under
   * test, and stubs default plan-quota and product-count values using lenient mocking so that tests
   * focused on other paths are not broken by unnecessary stub verification.
   *
   * @return void — sets up shared fixtures used across all test methods.
   */
  @BeforeEach
  void setUp() {
    UserContext ctx = new UserContext();
    ctx.setTenantId(TENANT_ID);
    ctx.setUserId(USER_ID);
    UserContext.setCurrentUserContext(ctx);

    service = new ProductService(productRepository, eventPublisher, planService, productOutboxService, meterRegistry);

    // Lenient: only createProduct tests use these; get/update/delete tests don't.
    lenient().when(planService.resolvePlanQuota(TENANT_ID)).thenReturn(100);
    lenient().when(productRepository.countByTenantIdAndStatusNot(TENANT_ID, ProductStatus.DELETED)).thenReturn(0L);
  }

  /**
   * Clears the {@link UserContext} thread-local after each test to prevent tenant/user state from
   * leaking into subsequent test executions.
   *
   * @return void — resets thread-local user context state.
   */
  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  // -----------------------------------------------------------------------
  // createProduct
  // -----------------------------------------------------------------------

  /**
   * Verifies that a new product is persisted with {@link ProductStatus#DRAFT} status and the correct
   * tenant ID when the tenant's product count is below the plan quota.
   *
   * @return void — asserts the saved product's status and tenant binding.
   */
  @Test
  void createProduct_underQuota_savesProductInDraftStatus() {
    ProductDTO request = buildRequest("PROD-001", "test-product-001");
    when(productRepository.findByCodeAndTenantIdAndStatusNot("PROD-001", TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.empty());
    when(productRepository.findBySlugAndTenantIdAndStatusNot("test-product-001", TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.empty());
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.createProduct(request);

    ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.DRAFT);
    assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    assertThat(captor.getValue().getCode()).isEqualTo("PROD-001");
  }

  /**
   * Verifies that when the incoming {@link ProductDTO} carries a null price history list, the service
   * automatically creates an initial price-history entry using the product's current price.
   *
   * @return void — asserts that exactly one price-history entry is saved with the request price.
   */
  @Test
  void createProduct_noPriceHistoryInRequest_createsInitialPriceEntry() {
    ProductDTO request = buildRequest("PROD-002", "test-product-002");
    request.setPriceHistory(null);
    when(productRepository.findByCodeAndTenantIdAndStatusNot(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(productRepository.findBySlugAndTenantIdAndStatusNot(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.createProduct(request);

    ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository).save(captor.capture());
    assertThat(captor.getValue().getPriceHistory()).hasSize(1);
    assertThat(captor.getValue().getPriceHistory().get(0).getPrice()).isEqualByComparingTo(BigDecimal.TEN);
  }

  /**
   * Verifies that attempting to create a product when the tenant's active product count equals the
   * plan quota throws a {@link QuotaExceededException} and no product is persisted.
   *
   * @return void — asserts that {@code QuotaExceededException} is thrown and {@code save} is never called.
   */
  @Test
  void createProduct_quotaExceeded_throwsQuotaExceededException() {
    when(productRepository.countByTenantIdAndStatusNot(TENANT_ID, ProductStatus.DELETED)).thenReturn(100L);
    when(planService.resolvePlanQuota(TENANT_ID)).thenReturn(100);

    assertThatThrownBy(() -> service.createProduct(buildRequest("PROD-003", "test-003")))
        .isInstanceOf(QuotaExceededException.class)
        .hasMessageContaining(TENANT_ID.toString());

    verify(productRepository, never()).save(any());
  }

  /**
   * Verifies that attempting to create a product with a code that already exists for the tenant throws
   * a {@link RuntimeException} containing the duplicate code and does not persist a new product.
   *
   * @return void — asserts that a conflict error is thrown and {@code save} is never called.
   */
  @Test
  void createProduct_duplicateCode_throwsRuntimeException() {
    when(productRepository.findByCodeAndTenantIdAndStatusNot("DUP-CODE", TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(buildProduct(1L, "DUP-CODE", "other-slug")));

    assertThatThrownBy(() -> service.createProduct(buildRequest("DUP-CODE", "new-slug")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DUP-CODE");

    verify(productRepository, never()).save(any());
  }

  /**
   * Verifies that attempting to create a product with a URL slug that already exists for the tenant throws
   * a {@link RuntimeException} containing the duplicate slug and does not persist a new product.
   *
   * @return void — asserts that a conflict error is thrown and {@code save} is never called.
   */
  @Test
  void createProduct_duplicateSlug_throwsRuntimeException() {
    when(productRepository.findByCodeAndTenantIdAndStatusNot(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(productRepository.findBySlugAndTenantIdAndStatusNot("dup-slug", TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(buildProduct(1L, "OTHER-CODE", "dup-slug")));

    assertThatThrownBy(() -> service.createProduct(buildRequest("NEW-CODE", "dup-slug")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("dup-slug");

    verify(productRepository, never()).save(any());
  }

  /**
   * Verifies that when no {@link UserContext} is present in the thread-local, the service falls back to
   * the tenant ID supplied in the {@link ProductDTO} request body and sets {@code createdBy} to
   * {@code "system"}.
   *
   * @return void — asserts the saved product's tenant ID and creator marker when context is absent.
   */
  @Test
  void createProduct_nullUserContext_usesTenantIdFromRequest() {
    UserContext.clear(); // no thread-local context

    ProductDTO request = buildRequest("PROD-004", "test-004");
    request.setTenantId(TENANT_ID); // tenant comes from request

    // re-stub for TENANT_ID (setUp stubs still apply but LENIENT)
    when(productRepository.findByCodeAndTenantIdAndStatusNot(any(), eq(TENANT_ID), any()))
        .thenReturn(Optional.empty());
    when(productRepository.findBySlugAndTenantIdAndStatusNot(any(), eq(TENANT_ID), any()))
        .thenReturn(Optional.empty());
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.createProduct(request);

    ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    assertThat(captor.getValue().getCreatedBy()).isEqualTo("system");
  }

  // -----------------------------------------------------------------------
  // getProductById
  // -----------------------------------------------------------------------

  /**
   * Verifies that retrieving an existing product by ID returns a {@link ProductDTO} with the matching
   * identifier, code, and tenant ID.
   *
   * @return void — asserts that the returned DTO fields match the persisted product.
   */
  @Test
  void getProductById_found_returnsMappedDTO() {
    Product product = buildProduct(42L, "CODE-42", "slug-42");
    when(productRepository.findByIdAndTenantIdAndStatusNot(42L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(product));

    ProductDTO result = service.getProductById(42L);

    assertThat(result.getId()).isEqualTo(42L);
    assertThat(result.getCode()).isEqualTo("CODE-42");
    assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
  }

  /**
   * Verifies that requesting a product ID that does not exist for the tenant throws a
   * {@link RuntimeException} whose message contains the requested ID.
   *
   * @return void — asserts that a not-found error is thrown with the missing ID in its message.
   */
  @Test
  void getProductById_notFound_throwsRuntimeException() {
    when(productRepository.findByIdAndTenantIdAndStatusNot(99L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProductById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("99");
  }

  // -----------------------------------------------------------------------
  // updateProduct
  // -----------------------------------------------------------------------

  /**
   * Verifies that updating a product when no price change occurs enqueues exactly one
   * {@code product.updated} outbox event.
   *
   * @return void — asserts that the product-updated outbox event is enqueued after a successful update.
   */
  @Test
  void updateProduct_happyPath_enqueuesProductUpdatedEvent() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1");
    ProductDTO request = buildRequest("CODE-1", "slug-1"); // same code + slug → no uniqueness re-check
    request.setPrice(BigDecimal.TEN); // same price as existing
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.updateProduct(1L, request);

    verify(productOutboxService).enqueue(
        eq("product"), any(), eq("product.updated"), any(), any(), any());
  }

  /**
   * Verifies that updating a product's price enqueues two outbox events: a general
   * {@code product.updated} event and a dedicated {@code product.price.updated} event.
   *
   * @return void — asserts that both outbox event types are enqueued when the price changes.
   */
  @Test
  void updateProduct_priceChanged_enqueuesProductAndPriceUpdatedEvents() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1");
    // existing price is 10, new price is 99
    ProductDTO request = buildRequest("CODE-1", "slug-1");
    request.setPrice(BigDecimal.valueOf(99));
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.updateProduct(1L, request);

    verify(productOutboxService, times(2)).enqueue(any(), any(), any(), any(), any(), any());
    verify(productOutboxService).enqueue(
        eq("product"), any(), eq("product.price.updated"), any(), any(), any());
  }

  /**
   * Verifies that updating a product without changing its price results in only one outbox event
   * being enqueued and that no {@code product.price.updated} event is produced.
   *
   * @return void — asserts that the price-updated outbox event is not enqueued when the price is unchanged.
   */
  @Test
  void updateProduct_priceUnchanged_doesNotEnqueuePriceEvent() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1");
    ProductDTO request = buildRequest("CODE-1", "slug-1");
    request.setPrice(BigDecimal.TEN); // same as existing
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.updateProduct(1L, request);

    verify(productOutboxService, times(1)).enqueue(any(), any(), any(), any(), any(), any());
    verify(productOutboxService, never()).enqueue(
        any(), any(), eq("product.price.updated"), any(), any(), any());
  }

  /**
   * Verifies that transitioning a product's status from {@link ProductStatus#DRAFT} to
   * {@link ProductStatus#ACTIVE} increments the {@code product_activated_total} Micrometer counter by one.
   *
   * @return void — asserts that the activation counter is incremented when a product is first activated.
   */
  @Test
  void updateProduct_statusChangedToActive_incrementsActivatedCounter() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1"); // DRAFT
    ProductDTO request = buildRequest("CODE-1", "slug-1");
    request.setPrice(BigDecimal.TEN);
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.updateProduct(1L, request);

    assertThat(meterRegistry.counter("product_activated_total").count()).isEqualTo(1.0);
  }

  /**
   * Verifies that updating a product that is already in {@link ProductStatus#ACTIVE} status does not
   * increment the {@code product_activated_total} counter, preventing double-counting of activations.
   *
   * @return void — asserts that the activation counter remains at zero when no status transition occurs.
   */
  @Test
  void updateProduct_statusAlreadyActive_doesNotIncrementCounter() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1");
    existing.setStatus(ProductStatus.ACTIVE);
    ProductDTO request = buildRequest("CODE-1", "slug-1");
    request.setPrice(BigDecimal.TEN);
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.updateProduct(1L, request);

    assertThat(meterRegistry.counter("product_activated_total").count()).isEqualTo(0.0);
  }

  /**
   * Verifies that attempting to change a product's code to one that already exists for the tenant throws
   * a {@link RuntimeException} containing the conflicting code and does not persist the change.
   *
   * @return void — asserts that a conflict error is thrown and {@code save} is never called.
   */
  @Test
  void updateProduct_duplicateCodeOnChange_throwsRuntimeException() {
    Product existing = buildProduct(1L, "OLD-CODE", "slug-1");
    ProductDTO request = buildRequest("NEW-CODE", "slug-1");
    request.setStatus(ProductStatus.DRAFT);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.findByCodeAndTenantIdAndStatusNot("NEW-CODE", TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(buildProduct(2L, "NEW-CODE", "other-slug")));

    assertThatThrownBy(() -> service.updateProduct(1L, request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("NEW-CODE");

    verify(productRepository, never()).save(any());
  }

  /**
   * Verifies that updating a product whose persisted image, attribute, and price-history collections
   * are null does not throw a NullPointerException — regression guard for the null-guard fix in
   * {@code updateProductImages}, {@code updateProductAttributes}, and {@code updateProductPrices}.
   *
   * @return void — asserts that no exception is thrown when the product's sub-collections are null.
   */
  @Test
  void updateProduct_existingCollectionsNull_doesNotThrowNPE() {
    Product existing = buildProduct(1L, "CODE-1", "slug-1");
    existing.setImages(null);
    existing.setAttributes(null);
    existing.setPriceHistory(null);
    ProductDTO request = buildRequest("CODE-1", "slug-1");
    request.setStatus(ProductStatus.ACTIVE);

    when(productRepository.findByIdAndTenantIdAndStatusNot(1L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(Optional.of(existing));
    when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    assertThatCode(() -> service.updateProduct(1L, request)).doesNotThrowAnyException();
  }

  // -----------------------------------------------------------------------
  // deleteProduct
  // -----------------------------------------------------------------------

  /**
   * Verifies that deleting an existing product triggers a repository call to set its status to
   * {@link ProductStatus#DELETED} (soft delete).
   *
   * @return void — asserts that {@code updateStatus} is called with {@code DELETED} for the product ID.
   */
  @Test
  void deleteProduct_exists_updatesStatusToDeleted() {
    when(productRepository.existsByIdAndTenantIdAndStatusNot(5L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(true);

    service.deleteProduct(5L);

    verify(productRepository).updateStatus(5L, ProductStatus.DELETED);
  }

  /**
   * Verifies that attempting to delete a product ID that does not exist for the tenant throws a
   * {@link RuntimeException} containing the missing ID and does not invoke the status-update repository method.
   *
   * @return void — asserts that a not-found error is thrown and {@code updateStatus} is never called.
   */
  @Test
  void deleteProduct_notFound_throwsRuntimeException() {
    when(productRepository.existsByIdAndTenantIdAndStatusNot(77L, TENANT_ID, ProductStatus.DELETED))
        .thenReturn(false);

    assertThatThrownBy(() -> service.deleteProduct(77L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("77");

    verify(productRepository, never()).updateStatus(any(), any());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Builds a {@link ProductDTO} with the supplied code and slug, pre-populated with sensible defaults
   * (price {@code BigDecimal.TEN}, currency {@code USD}, status {@code DRAFT}, tenant {@code TENANT_ID}).
   *
   * @param code the product code to assign.
   * @param slug the URL slug to assign.
   * @return a fully populated {@link ProductDTO} suitable for use in create or update test scenarios.
   */
  private ProductDTO buildRequest(String code, String slug) {
    return ProductDTO.builder()
        .code(code)
        .name("Test Product")
        .slug(slug)
        .categoryId(1L)
        .price(BigDecimal.TEN)
        .currency("USD")
        .status(ProductStatus.DRAFT)
        .tenantId(TENANT_ID)
        .images(new ArrayList<>())
        .attributes(new ArrayList<>())
        .priceHistory(new ArrayList<>())
        .build();
  }

  /**
   * Builds a {@link Product} entity with the supplied identifier, code, and slug, pre-populated with
   * the same defaults used by {@link #buildRequest(String, String)} so that request/entity comparisons
   * are consistent across tests.
   *
   * @param id the product's primary-key identifier.
   * @param code the product code to assign.
   * @param slug the URL slug to assign.
   * @return a fully populated {@link Product} entity for use as a repository stub return value.
   */
  private Product buildProduct(Long id, String code, String slug) {
    return Product.builder()
        .id(id)
        .code(code)
        .name("Test Product")
        .slug(slug)
        .categoryId(1L)
        .price(BigDecimal.TEN)
        .currency("USD")
        .status(ProductStatus.DRAFT)
        .tenantId(TENANT_ID)
        .images(new ArrayList<>())
        .attributes(new ArrayList<>())
        .priceHistory(new ArrayList<>())
        .build();
  }
}
