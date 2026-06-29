package huynv.fileservice.domain;

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
import lombok.Getter;

/**
 * Persists short-lived download tickets that bind file downloads to tenant and user context.
 */
@Getter
@Entity
@Table(name = "download_tickets")
public class DownloadTicket {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "single_use", nullable = false)
    private boolean singleUse;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DownloadTicket() {}

    /**
     * Creates a download ticket that can later be redeemed for a storage download.
     *
     * @param id Stable ticket identifier.
     * @param tokenHash SHA-256 hash of the opaque ticket token.
     * @param tenantId Tenant identifier bound to the ticket.
     * @param userId Optional user identifier bound to the ticket.
     * @param fileId File identifier bound to the ticket.
     * @param singleUse Whether the ticket may only be redeemed once.
     * @param expiresAt Ticket expiration timestamp.
     * @return Initializes a new download ticket entity.
     */
    public DownloadTicket(
        UUID id,
        String tokenHash,
        UUID tenantId,
        UUID userId,
        UUID fileId,
        boolean singleUse,
        Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = userId;
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.singleUse = singleUse;
        this.revoked = false;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Returns the bound tenant identifier.
     *
     * @return Returns the tenant identifier.
     */
    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * Returns the optional bound user identifier.
     *
     * @return Returns the user identifier, or null for anonymous/public tickets.
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Returns the bound file identifier.
     *
     * @return Returns the file identifier.
     */
    public UUID getFileId() {
        return fileId;
    }

    /**
     * Returns whether the ticket is single use.
     *
     * @return Returns true when the ticket must be consumed only once.
     */
    public boolean isSingleUse() {
        return singleUse;
    }

    /**
     * Returns the ticket expiry timestamp.
     *
     * @return Returns the ticket expiry timestamp.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns whether the ticket has been revoked.
     *
     * @return Returns true when the ticket is revoked.
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Returns whether the ticket has already been used.
     *
     * @return Returns true when the ticket has already been redeemed.
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Revokes the ticket so future redemption attempts are denied.
     *
     * @return Performs a side effect by marking the ticket as revoked.
     */
    public void revoke() {
        this.revoked = true;
    }

    /**
     * Marks the ticket as consumed when it is redeemed successfully.
     *
     * @return Performs a side effect by stamping the used-at timestamp.
     */
    public void markUsed() {
        this.usedAt = Instant.now();
    }

    /**
     * Returns the hashed token value used for lookups.
     *
     * @return Returns the hashed token value.
     */
    public String getTokenHash() {
        return tokenHash;
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
