package huynv.orderservice.repository;

import huynv.orderservice.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByTenantIdAndRequestIdAndApiName(Long tenantId, String requestId, String apiName);
}
