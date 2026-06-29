package huynv.fileservice.repository;

import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for file metadata records.
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, UUID> {

    /**
     * Loads a single file record owned by the provided tenant.
     *
     * @param tenantId Tenant identifier.
     * @param id File identifier.
     * @return Returns the matching file record when present.
     */
    Optional<FileRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Loads a file record by tenant and object key.
     *
     * @param tenantId Tenant identifier.
     * @param objectKey Stable object key.
     * @return Returns the matching file record when present.
     */
    Optional<FileRecord> findByTenantIdAndObjectKey(UUID tenantId, String objectKey);

    /**
     * Lists non-deleted file records for a tenant using pagination-friendly ordering.
     *
     * @param tenantId Tenant identifier.
     * @param pageable Page request.
     * @return Returns a page of tenant-owned file records.
     */
    @Query("select f from FileRecord f where f.tenantId = :tenantId and f.deletedAt is null order by f.createdAt desc")
    Page<FileRecord> findActiveByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Loads expired pending uploads for cleanup workflows.
     *
     * @param status Pending-upload lifecycle status.
     * @param threshold Timestamp threshold.
     * @return Returns matching pending file records.
     */
    List<FileRecord> findByStatusAndCreatedAtBefore(FileStatus status, Instant threshold);

    /**
     * Loads and pessimistically locks a single file record owned by the provided tenant.
     *
     * @param tenantId Tenant identifier.
     * @param id File identifier.
     * @return Returns the locked file record when present.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileRecord f where f.tenantId = :tenantId and f.id = :id")
    Optional<FileRecord> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Lists files in the supplied statuses for reconciliation processing.
     *
     * @param statuses Lifecycle statuses to inspect.
     * @param pageable Requested page size.
     * @return Returns a page of matching file records.
     */
    @Query("select f from FileRecord f where f.status in :statuses order by f.updatedAt asc")
    Page<FileRecord> findByStatusInOrderByUpdatedAtAsc(@Param("statuses") Collection<FileStatus> statuses, Pageable pageable);

    /**
     * Lists scan-failed files that are eligible for retry processing.
     *
     * @param status Scan-failed lifecycle status.
     * @param threshold Last update threshold that spaces retries.
     * @param pageable Requested page size.
     * @return Returns a page of retry-eligible file records.
     */
    @Query("select f from FileRecord f where f.status = :status and f.updatedAt <= :threshold order by f.updatedAt asc")
    Page<FileRecord> findRetryEligible(@Param("status") FileStatus status, @Param("threshold") Instant threshold, Pageable pageable);
}

