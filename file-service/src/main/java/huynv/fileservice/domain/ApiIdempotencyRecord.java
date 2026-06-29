package huynv.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Persists REST command idempotency state so multi-instance request retries return deterministic results.
 */
@Getter
@Entity
@Table(name = "api_idempotency")
public class ApiIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "request_path", nullable = false, length = 200)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ApiIdempotencyStatus status;

    @Column(name = "response_body", length = 8000)
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiIdempotencyRecord() {}

    /**
     * Creates a new idempotency record in PROCESSING state.
     *
     * @param tenantId Tenant identifier.
     * @param idempotencyKey Client-provided idempotency key.
     * @param requestPath Request path being protected.
     * @param requestHash Deterministic hash of the request payload.
     * @param expiresAt Expiration timestamp for record cleanup.
     * @return Initializes a new idempotency record.
     */
    public ApiIdempotencyRecord(
        UUID tenantId,
        String idempotencyKey,
        String requestPath,
        String requestHash,
        Instant expiresAt
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestPath = Objects.requireNonNull(requestPath, "requestPath");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.status = ApiIdempotencyStatus.PROCESSING;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Marks the record as completed and stores the serialized response body.
     *
     * @param responseBody Serialized response body to reuse for future duplicate requests.
     * @return Performs a side effect by transitioning the record to COMPLETED.
     */
    public void markCompleted(String responseBody) {
        this.status = ApiIdempotencyStatus.COMPLETED;
        this.responseBody = responseBody;
    }

    /**
     * Marks the record as failed and stores the serialized failure response body.
     *
     * @param responseBody Serialized failure response body.
     * @return Performs a side effect by transitioning the record to FAILED.
     */
    public void markFailed(String responseBody) {
        this.status = ApiIdempotencyStatus.FAILED;
        this.responseBody = responseBody;
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
