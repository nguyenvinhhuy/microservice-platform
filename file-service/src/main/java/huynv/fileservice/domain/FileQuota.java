package huynv.fileservice.domain;

import huynv.fileservice.exception.QuotaExceededException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists per-tenant storage quota usage to enforce upload limits safely across instances.
 */
@Entity
@Table(name = "file_quota")
public class FileQuota {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;

    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FileQuota() {
    }

    /**
     * Creates a new tenant quota row with default usage and configured quota cap.
     *
     * @param tenantId Tenant identifier.
     * @param quotaBytes Quota cap in bytes.
     * @return Initializes a tenant quota entity.
     */
    public FileQuota(UUID tenantId, long quotaBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.quotaBytes = quotaBytes;
        this.usedBytes = 0L;
    }

    /**
     * Reserves bytes against the tenant quota and rejects the request when the cap would be exceeded.
     *
     * @param bytes Number of bytes to reserve.
     * @return Performs a side effect by increasing usedBytes when capacity is available.
     */
    public void reserve(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must be non-negative");
        }
        if (usedBytes + bytes > quotaBytes) {
            throw new QuotaExceededException("QUOTA_EXCEEDED", "The tenant storage quota would be exceeded by this upload.");
        }
        usedBytes += bytes;
    }

    /**
     * Releases bytes from the tenant quota after deletion or failed uploads.
     *
     * @param bytes Number of bytes to release.
     * @return Performs a side effect by decreasing usedBytes without going negative.
     */
    public void release(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must be non-negative");
        }
        usedBytes = Math.max(0L, usedBytes - bytes);
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

    public UUID getTenantId() {
        return tenantId;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }
}

