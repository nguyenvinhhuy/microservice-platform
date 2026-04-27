package huynv.productservice.controller;

import huynv.productservice.dto.ProductDTO;
import huynv.productservice.model.IdempotencyKey;
import huynv.productservice.model.ProductStatus;
import huynv.productservice.service.IdempotencyService;
import huynv.productservice.service.ProductService;
import huynv.productservice.web.ProductIdempotencyKeyResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> createProduct(
            @Valid @RequestBody ProductDTO productRequest,
            @RequestHeader(value = ProductIdempotencyKeyResolver.IDEMPOTENCY_KEY_HEADER, required = false) UUID idempotencyKeyHeader,
            @RequestHeader(value = ProductIdempotencyKeyResolver.REQUEST_ID_HEADER, required = false) UUID requestIdHeader) {

        UUID requestId = ProductIdempotencyKeyResolver.resolve(idempotencyKeyHeader, requestIdHeader);

        if (requestId != null) {
            Optional<IdempotencyKey> existingKey = idempotencyService.getKey(requestId);
            if (existingKey.isPresent()) {
                return ResponseEntity.status(existingKey.get().getResponseStatus()).build();
            }
        }

        productService.createProduct(productRequest);

        if (requestId != null) {
            idempotencyService.saveKey(requestId, HttpStatus.CREATED.value(), null);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getAllProducts operation.
     *
     * @param pageable input parameter
     * @return getAllProducts result
     */
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productService.getAllProducts(pageable);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductById operation.
     *
     * @param id input parameter
     * @return getProductById result
     */
    public ProductDTO getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @GetMapping("/code/{code}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductByCode operation.
     *
     * @param code input parameter
     * @return getProductByCode result
     */
    public ProductDTO getProductByCode(@PathVariable String code) {
        return productService.getProductByCode(code);
    }

    @GetMapping("/slug/{slug}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductBySlug operation.
     *
     * @param slug input parameter
     * @return getProductBySlug result
     */
    public ProductDTO getProductBySlug(@PathVariable String slug) {
        return productService.getProductBySlug(slug);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    /**
     * updateProduct operation.
     *
     * @param id input parameter
     * @param productRequest input parameter
     * @return performs side effects defined by this operation
     */
    public void updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO productRequest) {
        productService.updateProduct(id, productRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    /**
     * deleteProduct operation.
     *
     * @param id input parameter
     * @return performs side effects defined by this operation
     */
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * searchProducts operation.
     *
     * @param keyword input parameter
     * @param pageable input parameter
     * @return searchProducts result
     */
    public Page<ProductDTO> searchProducts(@RequestParam String keyword, Pageable pageable) {
        return productService.searchProducts(keyword, pageable);
    }

    @GetMapping("/status/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductsByStatus operation.
     *
     * @param status input parameter
     * @param pageable input parameter
     * @return getProductsByStatus result
     */
    public Page<ProductDTO> getProductsByStatus(@PathVariable ProductStatus status, Pageable pageable) {
        return productService.getProductsByStatus(status, pageable);
    }

    @GetMapping("/status-list/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductsByStatusList operation.
     *
     * @param status input parameter
     * @return getProductsByStatusList result
     */
    public List<ProductDTO> getProductsByStatusList(@PathVariable ProductStatus status) {
        return productService.getProductsByStatusList(status);
    }

    @GetMapping("/category/{categoryId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    /**
     * getProductsByCategory operation.
     *
     * @param categoryId input parameter
     * @param pageable input parameter
     * @return getProductsByCategory result
     */
    public Page<ProductDTO> getProductsByCategory(@PathVariable Long categoryId, Pageable pageable) {
        return productService.getProductsByCategoryId(categoryId, pageable);
    }

    @GetMapping("/price-range")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_VIEWER')")
    public Page<ProductDTO> getProductsByPriceRange(
            @RequestParam(defaultValue = "ACTIVE") ProductStatus status,
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            Pageable pageable) {
        return productService.getProductsByPriceRange(status, minPrice, maxPrice, pageable);
    }
}
