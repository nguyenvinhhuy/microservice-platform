package huynv.fileservice.repository;

import huynv.fileservice.domain.MultipartUploadSession;
import huynv.fileservice.domain.MultipartUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Provides tenant-aware persistence access for multipart upload sessions.
 */
public interface MultipartUploadSessionRepository extends JpaRepository<MultipartUploadSession, UUID> {

    /**
     * Loads an active multipart session for the supplied tenant and file identifier.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param status Multipart upload status to match.
     * @return Returns the matching multipart session when present.
     */
    Optional<MultipartUploadSession> findByTenantIdAndFileIdAndStatus(UUID tenantId, UUID fileId, MultipartUploadStatus status);

    /**
     * Loads and pessimistically locks an active multipart session for mutation workflows.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param status Multipart upload status to match.
     * @return Returns the locked multipart session when present.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MultipartUploadSession s where s.tenantId = :tenantId and s.fileId = :fileId and s.status = :status")
    Optional<MultipartUploadSession> findByTenantIdAndFileIdAndStatusForUpdate(@Param("tenantId") UUID tenantId, @Param("fileId") UUID fileId, @Param("status") MultipartUploadStatus status);

    /**
     * Lists multipart sessions that have expired and still require cleanup processing.
     *
     * @param status Multipart upload status to match.
     * @param now Expiration cutoff timestamp.
     * @return Returns expired multipart sessions.
     */
    List<MultipartUploadSession> findByStatusAndExpiresAtBefore(MultipartUploadStatus status, Instant now);
}

