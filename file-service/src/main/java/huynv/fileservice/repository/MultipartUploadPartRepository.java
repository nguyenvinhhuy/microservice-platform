package huynv.fileservice.repository;

import huynv.fileservice.domain.MultipartUploadPart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence access for recorded multipart upload part metadata.
 */
public interface MultipartUploadPartRepository extends JpaRepository<MultipartUploadPart, UUID> {

    /**
     * Lists all recorded parts for the supplied multipart session in ascending part order.
     *
     * @param sessionId Multipart session identifier.
     * @return Returns the ordered list of completed parts.
     */
    List<MultipartUploadPart> findBySessionIdOrderByPartNumberAsc(UUID sessionId);

    /**
     * Loads a recorded multipart part by session and part number.
     *
     * @param sessionId Multipart session identifier.
     * @param partNumber Part number inside the upload.
     * @return Returns the matching multipart part when present.
     */
    Optional<MultipartUploadPart> findBySessionIdAndPartNumber(UUID sessionId, int partNumber);
}

