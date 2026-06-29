package huynv.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Persists the durable metadata recorded for a completed multipart upload part.
 */
@Getter
@Entity
@Table(name = "multipart_upload_part")
public class MultipartUploadPart {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Column(name = "etag", nullable = false, length = 255)
    private String eTag;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MultipartUploadPart() {}

    /**
     * Creates a persisted record for a completed multipart upload part.
     *
     * @param id Multipart part row identifier.
     * @param sessionId Multipart session identifier.
     * @param partNumber Part number inside the multipart upload.
     * @param eTag Storage-reported entity tag for the part.
     * @param checksumSha256 Optional client-reported checksum for the part.
     * @param sizeBytes Optional size of the uploaded part.
     */
    public MultipartUploadPart(UUID id, UUID sessionId, int partNumber, String eTag, String checksumSha256, Long sizeBytes) {
        this.id = Objects.requireNonNull(id, "id");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.partNumber = partNumber;
        this.eTag = Objects.requireNonNull(eTag, "eTag");
        this.checksumSha256 = checksumSha256;
        this.sizeBytes = sizeBytes;
    }

    /**
     * Replaces the completed part metadata when a client retries completion with the same part number.
     *
     * @param eTag Storage-reported entity tag for the part.
     * @param checksumSha256 Optional client-reported checksum for the part.
     * @param sizeBytes Optional size of the uploaded part.
     * @return Performs a side effect by replacing the stored part metadata.
     */
    public void update(String eTag, String checksumSha256, Long sizeBytes) {
        this.eTag = Objects.requireNonNull(eTag, "eTag");
        this.checksumSha256 = checksumSha256;
        this.sizeBytes = sizeBytes;
    }

    /**
     * Initializes persistence timestamps before the row is first stored.
     *
     * @return Performs a side effect by setting creation and update timestamps.
     */
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * Refreshes the update timestamp whenever the row changes.
     *
     * @return Performs a side effect by setting the updatedAt timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

