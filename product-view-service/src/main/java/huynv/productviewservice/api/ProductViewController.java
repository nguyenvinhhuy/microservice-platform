package huynv.productviewservice.api;

import huynv.productviewservice.model.ProductView;
import huynv.productviewservice.model.ProductViewId;
import huynv.productviewservice.repository.ProductViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Exposes read-only query APIs backed by the product_view table.
 */
@RestController
public class ProductViewController {

    private final ProductViewRepository productViewRepository;

    /**
     * Creates a read-only controller for product view queries.
     *
     * @param productViewRepository Repository used to query product view rows.
     * @return Initializes a product view controller instance.
     */
    public ProductViewController(ProductViewRepository productViewRepository) {
        this.productViewRepository = Objects.requireNonNull(productViewRepository, "productViewRepository");
    }

    /**
     * Lists product views for the current tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param page Page index starting at 0.
     * @param size Page size.
     * @return Returns a page of product view responses.
     */
    @GetMapping("/products")
    public Page<ProductViewResponse> listProducts(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        Page<ProductView> result = productViewRepository.findByIdTenantId(tenantId, PageRequest.of(page, size));
        return result.map(ProductViewController::toResponse);
    }

    /**
     * Loads a single product view row for the current tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param productId Product identifier to load.
     * @return Returns the product view response.
     */
    @GetMapping("/products/{id}")
    public ProductViewResponse getById(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("id") Long productId
    ) {
        ProductView view = productViewRepository.findById(new ProductViewId(tenantId, productId)).orElseThrow();
        return toResponse(view);
    }

    private static ProductViewResponse toResponse(ProductView view) {
        return new ProductViewResponse(
                view.getId().getProductId(),
                view.getName(),
                view.getPrice(),
                view.getStock(),
                view.getStatus(),
                view.getUpdatedAt()
        );
    }
}

