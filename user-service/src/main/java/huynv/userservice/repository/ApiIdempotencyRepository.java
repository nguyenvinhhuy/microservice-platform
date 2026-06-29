package huynv.userservice.repository;

import huynv.userservice.domain.ApiIdempotencyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for REST API idempotency records.
 */
public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotencyEntity, UUID> {

    /**
     * Loads an idempotency row with a pessimistic lock so concurrent retries observe a consistent state transition.
     *
     * @param tenantId Tenant identifier owning the request.
     * @param userId User identifier owning the request.
     * @param operation Logical operation name for the endpoint.
     * @param idempotencyKey Stable idempotency key supplied by the client.
     * @return Returns the matching idempotency row when present.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApiIdempotencyEntity> findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            UUID tenantId,
            UUID userId,
            String operation,
            String idempotencyKey
    );

    /**
     * Deletes expired idempotency rows so the table remains bounded.
     *
     * @param now Current timestamp used to identify expired rows.
     * @return Returns the number of deleted rows.
     */
    @Modifying
    @Query("delete from ApiIdempotencyEntity entity where entity.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}

