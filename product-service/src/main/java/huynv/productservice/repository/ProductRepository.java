package huynv.productservice.repository;

import huynv.productservice.model.Product;
import huynv.productservice.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndTenantIdAndStatusNot(Long id, Long tenantId, ProductStatus status);

    boolean existsByIdAndTenantIdAndStatusNot(Long id, Long tenantId, ProductStatus status);

    List<Product> findByTenantIdAndStatus(Long tenantId, ProductStatus status);

    Page<Product> findByTenantIdAndStatus(Long tenantId, ProductStatus status, Pageable pageable);

    Page<Product> findByTenantIdAndStatusNot(Long tenantId, ProductStatus status, Pageable pageable);

    Optional<Product> findByCodeAndTenantIdAndStatusNot(String code, Long tenantId, ProductStatus status);

    Optional<Product> findBySlugAndTenantIdAndStatusNot(String slug, Long tenantId, ProductStatus status);

    Page<Product> findByCategoryIdAndTenantIdAndStatusNot(Long categoryId, Long tenantId, ProductStatus status, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.status <> :deletedStatus
          AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
    """)
    Page<Product> searchByTenantId(@Param("tenantId") Long tenantId,
                                   @Param("keyword") String keyword,
                                   @Param("deletedStatus") ProductStatus deletedStatus,
                                   Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.status = :status
          AND p.price BETWEEN :minPrice AND :maxPrice
    """)
    Page<Product> findByTenantIdAndStatusAndPriceBetween(@Param("tenantId") Long tenantId,
                                                         @Param("status") ProductStatus status,
                                                         @Param("minPrice") BigDecimal minPrice,
                                                         @Param("maxPrice") BigDecimal maxPrice,
                                                         Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Product p
        SET p.status = :status
        WHERE p.id = :id
    """)
    void updateStatus(@Param("id") Long id,
                      @Param("status") ProductStatus status);

    // New method for Business Quota
    long countByTenantIdAndStatusNot(Long tenantId, ProductStatus status);
}
