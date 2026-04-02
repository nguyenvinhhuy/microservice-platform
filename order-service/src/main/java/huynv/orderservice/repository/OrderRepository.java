package huynv.orderservice.repository;

import huynv.orderservice.domain.Order;
import huynv.orderservice.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndTenantId(UUID id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdAndTenantIdAndStatus(UUID id, Long tenantId, OrderStatus status);

    List<Order> findByTenantIdAndStatusAndCreatedAtAfter(Long tenantId, OrderStatus status, OffsetDateTime createdAt);
}
