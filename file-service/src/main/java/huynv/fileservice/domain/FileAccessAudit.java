package huynv.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists file access and mutation audit records for operational diagnostics and forensic review.
 */
@Entity
@Table(name = "file_access_audit")
public class FileAccessAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FileAccessAudit() {
    }

    /**
     * Creates a new file access audit row.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param actorUserId Optional acting user identifier.
     * @param action Audited action name.
     * @param outcome Audited outcome name.
     * @param details Optional human-readable details.
     * @return Initializes a new audit entity.
     */
    public FileAccessAudit(UUID tenantId, UUID fileId, UUID actorUserId, String action, String outcome, String details) {
        this.tenantId = tenantId;
        this.fileId = fileId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.outcome = outcome;
        this.details = details;
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

