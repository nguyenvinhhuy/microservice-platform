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
 * Persists tenant-owned file metadata while the file bytes remain in object storage.
 */
@Getter
@Entity
@Table(name = "files")
public class FileRecord {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "bucket", nullable = false, length = 120)
    private String bucket;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_provider", nullable = false, length = 40)
    private String storageProvider;

    @Column(name = "object_version", nullable = false)
    private int objectVersion;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "encryption_mode", nullable = false, length = 40)
    private String encryptionMode;

    @Column(name = "encryption_key_reference", length = 255)
    private String encryptionKeyReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 40)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "malware_scan_status", nullable = false, length = 40)
    private MalwareScanStatus malwareScanStatus;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "pending_delete_at")
    private Instant pendingDeleteAt;

    @Column(name = "last_scan_attempt_at")
    private Instant lastScanAttemptAt;

    @Column(name = "scan_completed_at")
    private Instant scanCompletedAt;

    @Column(name = "scan_retry_count", nullable = false)
    private int scanRetryCount;

    @Column(name = "last_scan_error", length = 500)
    private String lastScanError;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FileRecord() {}

    /**
     * Creates a new file metadata record reserved for object upload and later lifecycle transitions.
     *
     * @param id File identifier.
     * @param tenantId Tenant identifier that owns the file.
     * @param ownerUserId User identifier that owns the file.
     * @param category Business category used for routing and authorization.
     * @param objectKey Stable object key inside the storage bucket.
     * @param bucket Storage bucket name.
     * @param originalFilename Original file name.
     * @param contentType MIME type accepted for the file.
     * @param sizeBytes File size in bytes.
     * @param checksumSha256 SHA-256 checksum in lowercase hexadecimal form.
     * @param visibility Visibility mode used for authorization.
     * @param metadataJson Optional metadata payload stored with the file.
     * @param retentionUntil Optional retention timestamp that blocks deletion before expiry.
     * @param encryptionMode Encryption mode metadata stored for the object.
     * @param encryptionKeyReference Optional encryption key reference stored for rotation readiness.
     * @return Initializes a reserved file metadata record.
     */
    public FileRecord(
        UUID id,
        UUID tenantId,
        UUID ownerUserId,
        String category,
        String objectKey,
        String bucket,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        FileVisibility visibility,
        String metadataJson,
        Instant retentionUntil,
        String encryptionMode,
        String encryptionKeyReference
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.category = Objects.requireNonNull(category, "category");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256");
        this.storageProvider = "MINIO";
        this.objectVersion = 1;
        this.retentionUntil = retentionUntil;
        this.encryptionMode = Objects.requireNonNull(encryptionMode, "encryptionMode");
        this.encryptionKeyReference = encryptionKeyReference;
        this.status = FileStatus.PENDING_UPLOAD;
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.malwareScanStatus = MalwareScanStatus.PENDING;
        this.metadataJson = metadataJson;
        this.scanRetryCount = 0;
    }

    /**
     * Transitions the file into the pending-scan state after bytes become durable in object storage.
     *
     * @param metadataJson Optional replacement metadata payload.
     * @return Performs a side effect by transitioning the file lifecycle state.
     */
    public void markPendingScan(String metadataJson) {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.PENDING_SCAN);
        this.status = FileStatus.PENDING_SCAN;
        this.malwareScanStatus = MalwareScanStatus.PENDING;
        this.lastScanAttemptAt = null;
        this.scanCompletedAt = null;
        this.lastScanError = null;
        if (metadataJson != null) {
            this.metadataJson = metadataJson;
        }
    }

    /**
     * Records the start of a malware scan attempt without changing the externally visible lifecycle state.
     *
     * @return Performs a side effect by updating the scan-attempt timestamp.
     */
    public void markScanAttemptStarted() {
        if (this.status == FileStatus.SCAN_FAILED) {
            FileLifecycleTransitionValidator.validate(this.status, FileStatus.PENDING_SCAN);
            this.status = FileStatus.PENDING_SCAN;
            this.malwareScanStatus = MalwareScanStatus.PENDING;
            this.scanCompletedAt = null;
            this.lastScanError = null;
        }
        this.lastScanAttemptAt = Instant.now();
    }

    /**
     * Marks the file as available after successful malware scanning.
     *
     * @return Performs a side effect by transitioning the file to the AVAILABLE state.
     */
    public void markAvailable() {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.AVAILABLE);
        this.status = FileStatus.AVAILABLE;
        this.malwareScanStatus = MalwareScanStatus.CLEAN;
        this.scanCompletedAt = Instant.now();
        this.lastScanError = null;
    }

    /**
     * Marks the file as quarantined due to malware or unrecoverable scan failure.
     *
     * @param malwareScanStatus Malware result reported by the scanning workflow.
     * @return Performs a side effect by transitioning the file to the QUARANTINED state.
     */
    public void markQuarantined(MalwareScanStatus malwareScanStatus) {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.QUARANTINED);
        this.status = FileStatus.QUARANTINED;
        this.malwareScanStatus = Objects.requireNonNull(malwareScanStatus, "malwareScanStatus");
        this.scanCompletedAt = Instant.now();
    }

    /**
     * Marks the file as scan-failed so a scheduler can retry it deterministically.
     *
     * @param errorMessage Human-readable scanner failure detail.
     * @return Performs a side effect by transitioning the file to the SCAN_FAILED state.
     */
    public void markScanFailed(String errorMessage) {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.SCAN_FAILED);
        this.status = FileStatus.SCAN_FAILED;
        this.malwareScanStatus = MalwareScanStatus.FAILED;
        this.scanRetryCount = this.scanRetryCount + 1;
        this.lastScanError = errorMessage;
        this.scanCompletedAt = Instant.now();
    }

    /**
     * Marks the upload as expired before asynchronous cleanup deletes the object bytes.
     *
     * @return Performs a side effect by transitioning the file to the UPLOAD_EXPIRED state.
     */
    public void markUploadExpired() {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.UPLOAD_EXPIRED);
        this.status = FileStatus.UPLOAD_EXPIRED;
        this.lastScanError = "The pending upload expired before confirmation.";
    }

    /**
     * Marks the file for deferred deletion before bytes are removed from object storage.
     *
     * @return Performs a side effect by transitioning the file to the DELETE_PENDING state.
     */
    public void markDeletePending() {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.DELETE_PENDING);
        this.status = FileStatus.DELETE_PENDING;
        this.pendingDeleteAt = Instant.now();
    }

    /**
     * Marks the file as soft-deleted and inaccessible for future reads.
     *
     * @return Performs a side effect by transitioning the file to the DELETED state.
     */
    public void markDeleted() {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.DELETED);
        this.status = FileStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    /**
     * Marks the file as archived after the active serving window closes.
     *
     * @return Performs a side effect by transitioning the file to the ARCHIVED state.
     */
    public void markArchived() {
        FileLifecycleTransitionValidator.validate(this.status, FileStatus.ARCHIVED);
        this.status = FileStatus.ARCHIVED;
    }

    /**
     * Initializes audit timestamps before the row is first persisted.
     *
     * @return Performs a side effect by setting required timestamps.
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
     * Updates the last-modified timestamp whenever the row changes.
     *
     * @return Performs a side effect by refreshing the updatedAt timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
