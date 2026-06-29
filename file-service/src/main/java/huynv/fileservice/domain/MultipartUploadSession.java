package huynv.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Persists the control-plane state for a multipart upload session before the object becomes durable.
 */
@Getter
@Entity
@Table(name = "multipart_upload_session")
public class MultipartUploadSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "bucket", nullable = false, length = 120)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "expected_size_bytes", nullable = false)
    private long expectedSizeBytes;

    @Column(name = "expected_checksum_sha256", nullable = false, length = 64)
    private String expectedChecksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 40)
    private FileVisibility visibility;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    @Column(name = "upload_id", nullable = false, length = 255)
    private String uploadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private MultipartUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MultipartUploadSession() {}

    /**
     * Creates a multipart upload session for a tenant-owned file object.
     *
     * @param id Multipart session identifier.
     * @param tenantId Tenant identifier that owns the upload.
     * @param ownerUserId User identifier that started the upload.
     * @param fileId File identifier reserved for the upload.
     * @param category Business category for routing and authorization.
     * @param bucket Storage bucket name.
     * @param objectKey Stable object key for the upload target.
     * @param originalFilename Original file name.
     * @param contentType MIME type expected for the upload.
     * @param expectedSizeBytes Expected object size in bytes.
     * @param expectedChecksumSha256 Expected full-object SHA-256 checksum.
     * @param visibility Visibility mode used for later authorization.
     * @param metadataJson Optional metadata payload.
     * @param uploadId Native object-storage multipart upload identifier.
     * @param expiresAt Session expiration timestamp.
     */
    public MultipartUploadSession(
            UUID id,
            UUID tenantId,
            UUID ownerUserId,
            UUID fileId,
            String category,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long expectedSizeBytes,
            String expectedChecksumSha256,
            FileVisibility visibility,
            String metadataJson,
            String uploadId,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.category = Objects.requireNonNull(category, "category");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.expectedSizeBytes = expectedSizeBytes;
        this.expectedChecksumSha256 = Objects.requireNonNull(expectedChecksumSha256, "expectedChecksumSha256");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.metadataJson = metadataJson;
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = MultipartUploadStatus.INITIATED;
    }

    /**
     * Returns whether the multipart session is still active and eligible for part uploads.
     *
     * @return Returns true when the session remains in the initiated state and is not expired.
     */
    public boolean isActive() {
        return status == MultipartUploadStatus.INITIATED && expiresAt.isAfter(Instant.now());
    }

    /**
     * Marks the multipart session as completed after the object store assembles all parts.
     *
     * @return Performs a side effect by transitioning the session into the completed state.
     */
    public void markCompleted() {
        this.status = MultipartUploadStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the multipart session as aborted by the user or a cleanup workflow.
     *
     * @return Performs a side effect by transitioning the session into the aborted state.
     */
    public void markAborted() {
        this.status = MultipartUploadStatus.ABORTED;
    }

    /**
     * Marks the multipart session as expired once its upload window closes.
     *
     * @return Performs a side effect by transitioning the session into the expired state.
     */
    public void markExpired() {
        this.status = MultipartUploadStatus.EXPIRED;
    }

    /**
     * Replaces the optional metadata payload associated with the session.
     *
     * @param metadataJson Updated metadata payload.
     * @return Performs a side effect by replacing the stored metadata payload.
     */
    public void updateMetadata(String metadataJson) {
        this.metadataJson = metadataJson;
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

