package huynv.orderservice.repository;

import huynv.orderservice.domain.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID> {

    Optional<OrderPayment> findByOrderIdAndOrderTenantId(UUID orderId, Long tenantId);
}
