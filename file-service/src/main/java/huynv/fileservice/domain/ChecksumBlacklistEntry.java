package huynv.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists known-malicious checksums so repeated uploads can be quarantined without rescanning.
 */
@Entity
@Table(name = "checksum_blacklist")
public class ChecksumBlacklistEntry {

    @Id
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "source", nullable = false, length = 80)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ChecksumBlacklistEntry() {
    }

    /**
     * Creates a checksum blacklist entry for a malicious object fingerprint.
     *
     * @param checksumSha256 SHA-256 checksum that must be blocked.
     * @param tenantId Optional tenant identifier that observed the malicious object.
     * @param reason Human-readable blacklist reason.
     * @param source Source that produced the blacklist entry.
     * @param expiresAt Optional expiry timestamp for temporary blacklist entries.
     * @return Initializes a checksum blacklist entity.
     */
    public ChecksumBlacklistEntry(String checksumSha256, UUID tenantId, String reason, String source, Instant expiresAt) {
        this.checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256");
        this.tenantId = tenantId;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.source = Objects.requireNonNull(source, "source");
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the blocked checksum value.
     *
     * @return Returns the blocked SHA-256 checksum.
     */
    public String getChecksumSha256() {
        return checksumSha256;
    }

    /**
     * Returns the blacklist reason.
     *
     * @return Returns the stored blacklist reason.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the optional expiry timestamp.
     *
     * @return Returns the expiry timestamp, or null when the blacklist entry does not expire.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Initializes the creation timestamp before the row is first persisted.
     *
     * @return Performs a side effect by setting the createdAt timestamp.
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

