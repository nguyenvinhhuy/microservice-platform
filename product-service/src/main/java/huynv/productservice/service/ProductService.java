package huynv.productservice.service;

import huynv.event.product.ProductPriceUpdatedEvent;
import huynv.event.product.ProductUpdatedEvent;
import huynv.productservice.context.UserContext;
import huynv.productservice.dto.ProductAttributeDTO;
import huynv.productservice.dto.ProductDTO;
import huynv.productservice.dto.ProductImageDTO;
import huynv.productservice.dto.ProductPriceDTO;
import huynv.productservice.exception.QuotaExceededException;
import huynv.productservice.model.Product;
import huynv.productservice.model.ProductAttribute;
import huynv.productservice.model.ProductImage;
import huynv.productservice.model.ProductPrice;
import huynv.productservice.model.ProductStatus;
import huynv.productservice.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Validated
public class ProductService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlanService planService; // Inject PlanService
    private final ProductOutboxService productOutboxService;
    private final Counter productCreatedCounter;
    private final Counter productActivatedCounter;

    /**
     * Initializes product service dependencies and metrics counters.
     *
     * @param productRepository Repository for tenant-scoped product persistence.
     * @param eventPublisher Publisher for internal domain events.
     * @param planService Service used to resolve tenant plan quota limits.
     * @param meterRegistry Registry used to register product metrics counters.
     * @return Registers Micrometer counters for product creation and activation.
     */
    public ProductService(ProductRepository productRepository,
                          ApplicationEventPublisher eventPublisher,
                          PlanService planService,
                          ProductOutboxService productOutboxService,
                          MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.planService = planService;
        this.productOutboxService = productOutboxService;

        this.productCreatedCounter = Counter.builder("product_created_total")
                .description("Total number of products created")
                .register(meterRegistry);

        this.productActivatedCounter = Counter.builder("product_activated_total")
                .description("Total number of products activated")
                .register(meterRegistry);
    }

    /**
     * Creates a new product for the current tenant and enforces plan quota and uniqueness rules.
     *
     * @param productRequest Product request payload to persist.
     * @return Persists a new product in DRAFT status and increments creation metrics.
     */
    @Transactional
    public void createProduct(ProductDTO productRequest) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : productRequest.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required for product creation.");
        }
        productRequest.setTenantId(tenantId); // Ensure tenantId is set from context

        // Business Quota Check (Layer 4)
        int currentProductCount = (int) productRepository.countByTenantIdAndStatusNot(tenantId, ProductStatus.DELETED);
        int quota = planService.resolvePlanQuota(tenantId);
        if (currentProductCount >= quota) {
            throw new QuotaExceededException("Tenant " + tenantId + " has exceeded the product creation quota of " + quota);
        }

        // Check for unique code
        if (productRepository.findByCodeAndTenantIdAndStatusNot(productRequest.getCode(), tenantId, ProductStatus.DELETED).isPresent()) {
            throw new RuntimeException("Product with code " + productRequest.getCode() + " already exists.");
        }
        // Check for unique slug
        if (productRepository.findBySlugAndTenantIdAndStatusNot(productRequest.getSlug(), tenantId, ProductStatus.DELETED).isPresent()) {
            throw new RuntimeException("Product with slug " + productRequest.getSlug() + " already exists.");
        }

        Product product = Product.builder()
                .code(productRequest.getCode())
                .name(productRequest.getName())
                .slug(productRequest.getSlug())
                .shortDescription(productRequest.getShortDescription())
                .description(productRequest.getDescription())
                .brand(productRequest.getBrand())
                .categoryId(productRequest.getCategoryId())
                .price(productRequest.getPrice())
                .currency(productRequest.getCurrency())
                .status(ProductStatus.DRAFT) // Default status
                .thumbnailUrl(productRequest.getThumbnailUrl())
                .ratingAverage(productRequest.getRatingAverage())
                .ratingCount(productRequest.getRatingCount())
                .createdAt(LocalDateTime.now())
                .createdBy(userContext != null && userContext.getUserId() != null ? userContext.getUserId().toString() : "system")
                .tenantId(tenantId) // Set tenantId from context
                .build();

        // Set images
        if (productRequest.getImages() != null && !productRequest.getImages().isEmpty()) {
            product.setImages(productRequest.getImages().stream()
                    .map(imageDTO -> mapToProductImage(imageDTO, product))
                    .collect(Collectors.toList()));
        }

        // Set attributes
        if (productRequest.getAttributes() != null && !productRequest.getAttributes().isEmpty()) {
            product.setAttributes(productRequest.getAttributes().stream()
                    .map(attributeDTO -> mapToProductAttribute(attributeDTO, product))
                    .collect(Collectors.toList()));
        }

        // Set price history (initial price)
        if (productRequest.getPriceHistory() != null && !productRequest.getPriceHistory().isEmpty()) {
            product.setPriceHistory(productRequest.getPriceHistory().stream()
                    .map(priceDTO -> mapToProductPrice(priceDTO, product))
                    .collect(Collectors.toList()));
        } else {
            // If no price history is provided, create one from the main price field
            ProductPrice initialPrice = ProductPrice.builder()
                    .product(product)
                    .price(product.getPrice())
                    .currency(product.getCurrency())
                    .validFrom(LocalDateTime.now())
                    .build();
            product.setPriceHistory(List.of(initialPrice));
        }


        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
        productCreatedCounter.increment();
    }

    /**
     * getAllProducts operation.
     *
     * @param pageable input parameter
     * @return getAllProducts result
     */
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get all products.");
        }
        // Filter by tenantId and status
        return productRepository.findByTenantIdAndStatusNot(tenantId, ProductStatus.DELETED, pageable)
                .map(this::mapToProductResponse);
    }

    /**
     * Gets one product by id within the current tenant boundary.
     *
     * @param id product id
     * @return mapped product response for the tenant-scoped product id
     */
    public ProductDTO getProductById(Long id) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get product by ID.");
        }
        Product product = productRepository.findByIdAndTenantIdAndStatusNot(id, tenantId, ProductStatus.DELETED)
                .orElseThrow(() -> new RuntimeException("Product not found or is deleted with id: " + id));
        return mapToProductResponse(product);
    }

    /**
     * getProductByCode operation.
     *
     * @param code input parameter
     * @return getProductByCode result
     */
    public ProductDTO getProductByCode(String code) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get product by code.");
        }
        Product product = productRepository.findByCodeAndTenantIdAndStatusNot(code, tenantId, ProductStatus.DELETED)
                .orElseThrow(() -> new RuntimeException("Product not found or is deleted with code: " + code));
        return mapToProductResponse(product);
    }

    /**
     * getProductBySlug operation.
     *
     * @param slug input parameter
     * @return getProductBySlug result
     */
    public ProductDTO getProductBySlug(String slug) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get product by slug.");
        }
        Product product = productRepository.findBySlugAndTenantIdAndStatusNot(slug, tenantId, ProductStatus.DELETED)
                .orElseThrow(() -> new RuntimeException("Product not found or is deleted with slug: " + slug));
        return mapToProductResponse(product);
    }

    /**
     * Updates an existing product for the current tenant and publishes activation event on status change.
     *
     * @param id Product identifier to update.
     * @param productRequest Product request payload containing updated fields.
     * @return Updates product state, persists changes, and emits activation event when transitioning to ACTIVE.
     */
    @Transactional
    public void updateProduct(Long id, ProductDTO productRequest) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : productRequest.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required for product update.");
        }
        productRequest.setTenantId(tenantId); // Ensure tenantId is set from context

        Product product = productRepository.findByIdAndTenantIdAndStatusNot(id, tenantId, ProductStatus.DELETED)
                .orElseThrow(() -> new RuntimeException("Product not found or is deleted with id: " + id));

        // Check for unique code if changed
        if (!product.getCode().equals(productRequest.getCode())) {
            if (productRepository.findByCodeAndTenantIdAndStatusNot(productRequest.getCode(), tenantId, ProductStatus.DELETED).isPresent()) {
                throw new RuntimeException("Product with code " + productRequest.getCode() + " already exists for tenant " + tenantId + ".");
            }
        }
        // Check for unique slug if changed
        if (!product.getSlug().equals(productRequest.getSlug())) {
            if (productRepository.findBySlugAndTenantIdAndStatusNot(productRequest.getSlug(), tenantId, ProductStatus.DELETED).isPresent()) {
                throw new RuntimeException("Product with slug " + productRequest.getSlug() + " already exists for tenant " + tenantId + ".");
            }
        }

        ProductStatus oldStatus = product.getStatus();
        ProductStatus newStatus = productRequest.getStatus();
        java.math.BigDecimal oldPrice = product.getPrice();
        String oldCurrency = product.getCurrency();

        product.setCode(productRequest.getCode());
        product.setName(productRequest.getName());
        product.setSlug(productRequest.getSlug());
        product.setShortDescription(productRequest.getShortDescription());
        product.setDescription(productRequest.getDescription());
        product.setBrand(productRequest.getBrand());
        product.setCategoryId(productRequest.getCategoryId());
        product.setPrice(productRequest.getPrice());
        product.setCurrency(productRequest.getCurrency());
        product.setStatus(newStatus);
        product.setThumbnailUrl(productRequest.getThumbnailUrl());
        product.setRatingAverage(productRequest.getRatingAverage());
        product.setRatingCount(productRequest.getRatingCount());
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(userContext != null && userContext.getUserId() != null ? userContext.getUserId().toString() : "system");
        product.setTenantId(tenantId); // Ensure tenantId is set from context

        // Update images
        updateProductImages(product, productRequest.getImages());

        // Update attributes
        updateProductAttributes(product, productRequest.getAttributes());

        // Update price history
        updateProductPrices(product, productRequest.getPriceHistory());

        Product savedProduct = productRepository.save(product);
        log.info("Product {} is updated", product.getId());

        ProductUpdatedEvent updated = new ProductUpdatedEvent(
                tenantId,
                savedProduct.getId(),
                savedProduct.getCode(),
                savedProduct.getName(),
                savedProduct.getPrice(),
                savedProduct.getCurrency()
        );
        productOutboxService.enqueue(
                "product",
                "product-" + savedProduct.getId(),
                "product.updated",
                updated,
                "product-" + savedProduct.getId(),
                null
        );

        if (!java.util.Objects.equals(oldPrice, savedProduct.getPrice()) || !java.util.Objects.equals(oldCurrency, savedProduct.getCurrency())) {
            ProductPriceUpdatedEvent priceUpdated = new ProductPriceUpdatedEvent(
                    tenantId,
                    savedProduct.getId(),
                    savedProduct.getPrice(),
                    savedProduct.getCurrency()
            );
            productOutboxService.enqueue(
                    "product",
                    "product-" + savedProduct.getId(),
                    "product.price.updated",
                    priceUpdated,
                    "product-" + savedProduct.getId(),
                    null
            );
        }

        if (oldStatus != ProductStatus.ACTIVE && newStatus == ProductStatus.ACTIVE) {
            productActivatedCounter.increment();
        }
    }

    /**
     * Soft-deletes a product for the current tenant by marking status as DELETED.
     *
     * @param id Product identifier to delete.
     * @return Marks the product as deleted and prevents future reads in tenant-scoped queries.
     */
    @Transactional
    public void deleteProduct(Long id) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required for product deletion.");
        }

        if (!productRepository.existsByIdAndTenantIdAndStatusNot(id, tenantId, ProductStatus.DELETED)) {
            throw new RuntimeException("Product not found or is already deleted with id: " + id + " for tenant " + tenantId + ".");
        }
        productRepository.updateStatus(id, ProductStatus.DELETED);
        log.info("Product {} is deleted (soft delete)", id);
    }

    /**
     * getProductsByStatus operation.
     *
     * @param status input parameter
     * @param pageable input parameter
     * @return getProductsByStatus result
     */
    public Page<ProductDTO> getProductsByStatus(ProductStatus status, Pageable pageable) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get products by status.");
        }
        return productRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(this::mapToProductResponse);
    }

    /**
     * Gets products by status as a list within the current tenant boundary.
     *
     * @param status product status filter
     * @return tenant-scoped product list mapped to response dto
     */
    public List<ProductDTO> getProductsByStatusList(ProductStatus status) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get products by status.");
        }
        return productRepository.findByTenantIdAndStatus(tenantId, status)
                .stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    /**
     * searchProducts operation.
     *
     * @param keyword input parameter
     * @param pageable input parameter
     * @return searchProducts result
     */
    public Page<ProductDTO> searchProducts(String keyword, Pageable pageable) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to search products.");
        }
        return productRepository.searchByTenantId(tenantId, keyword, ProductStatus.DELETED, pageable)
                .map(this::mapToProductResponse);
    }

    /**
     * getProductsByCategoryId operation.
     *
     * @param categoryId input parameter
     * @param pageable input parameter
     * @return getProductsByCategoryId result
     */
    public Page<ProductDTO> getProductsByCategoryId(Long categoryId, Pageable pageable) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get products by category.");
        }
        return productRepository.findByCategoryIdAndTenantIdAndStatusNot(categoryId, tenantId, ProductStatus.DELETED, pageable)
                .map(this::mapToProductResponse);
    }

    /**
     * getProductsByPriceRange operation.
     *
     * @param status input parameter
     * @param minPrice input parameter
     * @param maxPrice input parameter
     * @param pageable input parameter
     * @return getProductsByPriceRange result
     */
    public Page<ProductDTO> getProductsByPriceRange(ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        UserContext userContext = UserContext.getCurrentUserContext();
        Long tenantId = userContext != null ? userContext.getTenantId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is required to get products by price range.");
        }
        return productRepository.findByTenantIdAndStatusAndPriceBetween(tenantId, status, minPrice, maxPrice, pageable)
                .map(this::mapToProductResponse);
    }

    /**
     * mapToProductResponse operation.
     *
     * @param product input parameter
     * @return mapToProductResponse result
     */
    private ProductDTO mapToProductResponse(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .brand(product.getBrand())
                .categoryId(product.getCategoryId())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .status(product.getStatus())
                .thumbnailUrl(product.getThumbnailUrl())
                .ratingAverage(product.getRatingAverage())
                .ratingCount(product.getRatingCount())
                .tenantId(product.getTenantId())
                .images(product.getImages() != null ? product.getImages().stream().map(this::mapToProductImageDTO).collect(Collectors.toList()) : null)
                .attributes(product.getAttributes() != null ? product.getAttributes().stream().map(this::mapToProductAttributeDTO).collect(Collectors.toList()) : null)
                .priceHistory(product.getPriceHistory() != null ? product.getPriceHistory().stream().map(this::mapToProductPriceDTO).collect(Collectors.toList()) : null)
                .createdAt(product.getCreatedAt())
                .createdBy(product.getCreatedBy())
                .updatedAt(product.getUpdatedAt())
                .updatedBy(product.getUpdatedBy())
                .build();
    }

    /**
     * mapToProductImage operation.
     *
     * @param imageDTO input parameter
     * @param product input parameter
     * @return mapToProductImage result
     */
    private ProductImage mapToProductImage(ProductImageDTO imageDTO, Product product) {
        return ProductImage.builder()
                .id(imageDTO.getId())
                .product(product)
                .url(imageDTO.getUrl())
                .isPrimary(imageDTO.isPrimary())
                .sortOrder(imageDTO.getSortOrder())
                .build();
    }

    /**
     * mapToProductImageDTO operation.
     *
     * @param image input parameter
     * @return mapToProductImageDTO result
     */
    private ProductImageDTO mapToProductImageDTO(ProductImage image) {
        return ProductImageDTO.builder()
                .id(image.getId())
                .url(image.getUrl())
                .isPrimary(image.isPrimary())
                .sortOrder(image.getSortOrder())
                .build();
    }

    /**
     * mapToProductAttribute operation.
     *
     * @param attributeDTO input parameter
     * @param product input parameter
     * @return mapToProductAttribute result
     */
    private ProductAttribute mapToProductAttribute(ProductAttributeDTO attributeDTO, Product product) {
        return ProductAttribute.builder()
                .id(attributeDTO.getId())
                .product(product)
                .name(attributeDTO.getName())
                .value(attributeDTO.getValue())
                .build();
    }

    /**
     * mapToProductAttributeDTO operation.
     *
     * @param attribute input parameter
     * @return mapToProductAttributeDTO result
     */
    private ProductAttributeDTO mapToProductAttributeDTO(ProductAttribute attribute) {
        return ProductAttributeDTO.builder()
                .id(attribute.getId())
                .name(attribute.getName())
                .value(attribute.getValue())
                .build();
    }

    /**
     * mapToProductPrice operation.
     *
     * @param priceDTO input parameter
     * @param product input parameter
     * @return mapToProductPrice result
     */
    private ProductPrice mapToProductPrice(ProductPriceDTO priceDTO, Product product) {
        return ProductPrice.builder()
                .id(priceDTO.getId())
                .product(product)
                .price(priceDTO.getPrice())
                .currency(priceDTO.getCurrency())
                .validFrom(priceDTO.getValidFrom())
                .validTo(priceDTO.getValidTo())
                .build();
    }

    /**
     * mapToProductPriceDTO operation.
     *
     * @param price input parameter
     * @return mapToProductPriceDTO result
     */
    private ProductPriceDTO mapToProductPriceDTO(ProductPrice price) {
        return ProductPriceDTO.builder()
                .id(price.getId())
                .price(price.getPrice())
                .currency(price.getCurrency())
                .validFrom(price.getValidFrom())
                .validTo(price.getValidTo())
                .build();
    }

    /**
     * Synchronizes the product's image collection with the provided list of image DTOs.
     * Removes images that are no longer present, updates existing images in place,
     * and appends newly added images to the collection.
     *
     * @param product The product aggregate whose image collection will be modified.
     * @param newImageDTOs The desired image state as a list of DTOs; if null, no changes are made.
     * @return Modifies the product's image collection as a side effect; no value is returned.
     */
    private void updateProductImages(Product product, List<ProductImageDTO> newImageDTOs) {
        if (newImageDTOs == null) return;
        if (product.getImages() == null) {
            product.setImages(new java.util.ArrayList<>());
        }
        final List<ProductImage> existingImages = product.getImages();

        // Remove images not present in newImageDTOs
        existingImages.removeIf(existingImage ->
                newImageDTOs.stream().noneMatch(newImageDTO ->
                        newImageDTO.getId() != null && newImageDTO.getId().equals(existingImage.getId())));

        // Add or update images
        for (ProductImageDTO newImageDTO : newImageDTOs) {
            if (newImageDTO.getId() == null) {
                // Add new image
                existingImages.add(mapToProductImage(newImageDTO, product));
            } else {
                // Update existing image
                existingImages.stream()
                        .filter(img -> img.getId().equals(newImageDTO.getId()))
                        .findFirst()
                        .ifPresent(existingImage -> {
                            existingImage.setUrl(newImageDTO.getUrl());
                            existingImage.setPrimary(newImageDTO.isPrimary());
                            existingImage.setSortOrder(newImageDTO.getSortOrder());
                        });
            }
        }
    }

    /**
     * Synchronizes the product's attribute collection with the provided list of attribute DTOs.
     * Removes attributes that are no longer present, updates existing attributes in place,
     * and appends newly added attributes to the collection.
     *
     * @param product The product aggregate whose attribute collection will be modified.
     * @param newAttributeDTOs The desired attribute state as a list of DTOs; if null, no changes are made.
     * @return Modifies the product's attribute collection as a side effect; no value is returned.
     */
    private void updateProductAttributes(Product product, List<ProductAttributeDTO> newAttributeDTOs) {
        if (newAttributeDTOs == null) return;
        if (product.getAttributes() == null) {
            product.setAttributes(new java.util.ArrayList<>());
        }
        final List<ProductAttribute> existingAttributes = product.getAttributes();

        // Remove attributes not present in newAttributeDTOs
        existingAttributes.removeIf(existingAttribute ->
                newAttributeDTOs.stream().noneMatch(newAttributeDTO ->
                        newAttributeDTO.getId() != null && newAttributeDTO.getId().equals(existingAttribute.getId())));

        // Add or update attributes
        for (ProductAttributeDTO newAttributeDTO : newAttributeDTOs) {
            if (newAttributeDTO.getId() == null) {
                // Add new attribute
                existingAttributes.add(mapToProductAttribute(newAttributeDTO, product));
            } else {
                // Update existing attribute
                existingAttributes.stream()
                        .filter(attr -> attr.getId().equals(newAttributeDTO.getId()))
                        .findFirst()
                        .ifPresent(existingAttribute -> {
                            existingAttribute.setName(newAttributeDTO.getName());
                            existingAttribute.setValue(newAttributeDTO.getValue());
                        });
            }
        }
    }

    /**
     * Synchronizes the product's price history with the provided list of price DTOs.
     * Removes price entries that are no longer present, updates existing entries in place,
     * and appends newly added price entries to the collection.
     *
     * @param product The product aggregate whose price history will be modified.
     * @param newPriceDTOs The desired price history as a list of DTOs; if null, no changes are made.
     * @return Modifies the product's price history as a side effect; no value is returned.
     */
    private void updateProductPrices(Product product, List<ProductPriceDTO> newPriceDTOs) {
        if (newPriceDTOs == null) return;
        if (product.getPriceHistory() == null) {
            product.setPriceHistory(new java.util.ArrayList<>());
        }
        final List<ProductPrice> existingPrices = product.getPriceHistory();

        // Remove prices not present in newPriceDTOs
        existingPrices.removeIf(existingPrice ->
                newPriceDTOs.stream().noneMatch(newPriceDTO ->
                        newPriceDTO.getId() != null && newPriceDTO.getId().equals(existingPrice.getId())));

        // Add or update prices
        for (ProductPriceDTO newPriceDTO : newPriceDTOs) {
            if (newPriceDTO.getId() == null) {
                // Add new price
                existingPrices.add(mapToProductPrice(newPriceDTO, product));
            } else {
                // Update existing price
                existingPrices.stream()
                        .filter(price -> price.getId().equals(newPriceDTO.getId()))
                        .findFirst()
                        .ifPresent(existingPrice -> {
                            existingPrice.setPrice(newPriceDTO.getPrice());
                            existingPrice.setCurrency(newPriceDTO.getCurrency());
                            existingPrice.setValidFrom(newPriceDTO.getValidFrom());
                            existingPrice.setValidTo(newPriceDTO.getValidTo());
                        });
            }
        }
    }

}

