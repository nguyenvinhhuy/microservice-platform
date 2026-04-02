package huynv.productviewservice.service;

import huynv.productviewservice.model.ProductView;
import huynv.productviewservice.model.ProductViewId;
import huynv.productviewservice.repository.ProductViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Applies event-driven updates to the product_view table to keep query state current.
 */
@Service
public class ProductViewProjectionService {

    private final ProductViewRepository productViewRepository;

    /**
     * Creates a projection service that writes to the product_view table.
     *
     * @param productViewRepository Repository used to read and write product view rows.
     * @return Initializes a projection service instance.
     */
    public ProductViewProjectionService(ProductViewRepository productViewRepository) {
        this.productViewRepository = Objects.requireNonNull(productViewRepository, "productViewRepository");
    }

    /**
     * Upserts product name/price fields from product events.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param productId Product identifier updated by the event.
     * @param name Product name value.
     * @param price Product price value.
     * @param status Product status string used for query filtering.
     * @param updatedAt Timestamp used for last-update tracking.
     * @return Performs a side effect by persisting the updated view row.
     */
    @Transactional
    public void upsertProduct(Long tenantId, Long productId, String name, BigDecimal price, String status, OffsetDateTime updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(productId, "productId");

        ProductViewId id = new ProductViewId(tenantId, productId);
        ProductView view = productViewRepository.findById(id).orElseGet(ProductView::new);
        view.setId(id);
        if (name != null && !name.isBlank()) {
            view.setName(name);
        }
        if (price != null) {
            view.setPrice(price);
        }
        if (status != null && !status.isBlank()) {
            view.setStatus(status);
        }
        view.setUpdatedAt(updatedAt == null ? OffsetDateTime.now() : updatedAt);
        productViewRepository.save(view);
    }

    /**
     * Updates stock fields from inventory stock snapshot events.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param productId Product identifier updated by the event.
     * @param availableStock Available stock computed by inventory-service.
     * @param updatedAt Timestamp used for last-update tracking.
     * @return Performs a side effect by persisting updated stock fields.
     */
    @Transactional
    public void updateStock(Long tenantId, Long productId, Integer availableStock, OffsetDateTime updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(productId, "productId");

        ProductViewId id = new ProductViewId(tenantId, productId);
        ProductView view = productViewRepository.findById(id).orElseGet(ProductView::new);
        view.setId(id);
        view.setStock(availableStock);
        view.setUpdatedAt(updatedAt == null ? OffsetDateTime.now() : updatedAt);
        if (availableStock != null) {
            view.setStatus(availableStock > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        }
        productViewRepository.save(view);
    }
}

