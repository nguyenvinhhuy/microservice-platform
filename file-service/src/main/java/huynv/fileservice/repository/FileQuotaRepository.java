package huynv.fileservice.repository;

import huynv.fileservice.domain.FileQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for quota rows.
 */
public interface FileQuotaRepository extends JpaRepository<FileQuota, UUID> {

    /**
     * Loads and pessimistically locks the quota row for the provided tenant.
     *
     * @param tenantId Tenant identifier.
     * @return Returns the locked quota row when present.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from FileQuota q where q.tenantId = :tenantId")
    Optional<FileQuota> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}

