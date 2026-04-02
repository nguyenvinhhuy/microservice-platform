package huynv.orderservice.repository;

import huynv.orderservice.saga.OrderSaga;
import huynv.orderservice.saga.OrderSagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, Long> {

    Optional<OrderSaga> findByTenantIdAndOrderId(Long tenantId, UUID orderId);

    List<OrderSaga> findTop50ByStateInOrderByUpdatedAtAsc(Collection<OrderSagaState> states);
}
