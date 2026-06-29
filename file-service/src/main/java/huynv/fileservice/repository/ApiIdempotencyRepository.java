package huynv.fileservice.repository;

import huynv.fileservice.domain.ApiIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for REST idempotency rows.
 */
public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotencyRecord, Long> {

    /**
     * Loads a single idempotency record by tenant, request path, and key.
     *
     * @param tenantId Tenant identifier.
     * @param requestPath Request path under idempotency protection.
     * @param idempotencyKey Client-provided idempotency key.
     * @return Returns the matching record when present.
     */
    Optional<ApiIdempotencyRecord> findByTenantIdAndRequestPathAndIdempotencyKey(UUID tenantId, String requestPath, String idempotencyKey);

    /**
     * Deletes expired idempotency rows.
     *
     * @param threshold Expiration threshold.
     * @return Performs a side effect by removing expired records.
     */
    @Modifying
    @Query("delete from ApiIdempotencyRecord r where r.expiresAt < :threshold")
    void deleteExpired(@Param("threshold") Instant threshold);
}

