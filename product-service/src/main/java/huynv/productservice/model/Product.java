package huynv.productservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "t_products", indexes = {
        @Index(name = "idx_product_code", columnList = "code"),
        @Index(name = "idx_product_slug", columnList = "slug"),
        @Index(name = "idx_product_tenant_id", columnList = "tenantId"),
        @Index(name = "idx_product_status", columnList = "status"),
        @Index(name = "idx_product_tenant_status", columnList = "tenantId, status")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String slug;
    private String shortDescription;
    private String description;
    private String brand;
    private Long categoryId;
    private BigDecimal price;
    private String currency;

    @Enumerated(EnumType.STRING) // Store enum as String in DB
    private ProductStatus status;

    private String thumbnailUrl;
    private Double ratingAverage;
    private Integer ratingCount;

    @Column(nullable = false)
    private Long tenantId; // Added tenantId

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttribute> attributes;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductPrice> priceHistory;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private String createdBy;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String updatedBy;
}
