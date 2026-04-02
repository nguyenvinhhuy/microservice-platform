package huynv.productservice.dto;

import huynv.productservice.model.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {
    private Long id;

    @NotBlank(message = "Product code cannot be blank")
    @Size(max = 50, message = "Product code cannot exceed 50 characters")
    private String code;

    @NotBlank(message = "Product name cannot be blank")
    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Product slug cannot be blank")
    @Size(max = 255, message = "Product slug cannot exceed 255 characters")
    private String slug;

    @Size(max = 500, message = "Short description cannot exceed 500 characters")
    private String shortDescription;

    private String description;

    @Size(max = 100, message = "Brand name cannot exceed 100 characters")
    private String brand;

    @NotNull(message = "Category ID cannot be null")
    @Positive(message = "Category ID must be positive")
    private Long categoryId;

    @NotNull(message = "Price cannot be null")
    @PositiveOrZero(message = "Price must be positive or zero")
    private BigDecimal price;

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g., USD)")
    private String currency;

    @NotNull(message = "Product status cannot be null")
    private ProductStatus status;

    private String thumbnailUrl;

    @DecimalMin(value = "0.0", message = "Rating average must be at least 0.0")
    @DecimalMax(value = "5.0", message = "Rating average cannot exceed 5.0")
    private Double ratingAverage;

    @Min(value = 0, message = "Rating count cannot be negative")
    private Integer ratingCount;

    @NotNull(message = "Tenant ID cannot be null")
    @Positive(message = "Tenant ID must be positive")
    private Long tenantId;

    private List<ProductImageDTO> images;
    private List<ProductAttributeDTO> attributes;
    private List<ProductPriceDTO> priceHistory;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
