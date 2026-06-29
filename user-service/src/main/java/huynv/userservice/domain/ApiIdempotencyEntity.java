package huynv.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists idempotency state for REST write operations so retries remain safe across nodes.
 */
@Entity
@Table(
        name = "api_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_api_idempotency_scope", columnNames = {"tenant_id", "user_id", "operation", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_api_idempotency_expires_at", columnList = "expires_at"),
                @Index(name = "idx_api_idempotency_state_expires", columnList = "state,expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiIdempotencyEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "operation", nullable = false, length = 120, updatable = false)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ApiIdempotencyState state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Creates a new idempotency row in processing state for a single logical write operation.
     *
     * @param id Row identifier.
     * @param tenantId Tenant identifier owning the request.
     * @param userId User identifier owning the request.
     * @param operation Logical operation name for the REST endpoint.
     * @param idempotencyKey Stable idempotency key supplied by the client.
     * @param requestHash Stable request hash derived from the logical request payload.
     * @param expiresAt Expiration time after which the row may be purged.
     * @return Initializes a new idempotency entity in processing state.
     */
    public ApiIdempotencyEntity(
            UUID id,
            UUID tenantId,
            UUID userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Instant expiresAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.state = ApiIdempotencyState.PROCESSING;
        this.expiresAt = expiresAt;
    }

    /**
     * Marks the request as completed and stores the cached response payload.
     *
     * @param responseStatus HTTP status code returned to the caller.
     * @param responseBody Serialized response body to replay on duplicate requests.
     * @return Performs a side effect by transitioning the entity to completed state.
     */
    public void markCompleted(int responseStatus, String responseBody) {
        this.state = ApiIdempotencyState.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    /**
     * Marks the request as failed so the next caller can retry deterministically.
     *
     * @return Performs a side effect by transitioning the entity to failed state.
     */
    public void markFailed() {
        this.state = ApiIdempotencyState.FAILED;
        this.responseStatus = null;
        this.responseBody = null;
    }

    /**
     * Refreshes an existing row for a new processing attempt with the same logical request.
     *
     * @param requestHash Stable request hash for the new attempt.
     * @param expiresAt Expiration time for the refreshed row.
     * @return Performs a side effect by resetting the row to processing state.
     */
    public void restart(String requestHash, Instant expiresAt) {
        this.requestHash = requestHash;
        this.state = ApiIdempotencyState.PROCESSING;
        this.responseStatus = null;
        this.responseBody = null;
        this.expiresAt = expiresAt;
    }

    /**
     * Initializes timestamps before the row is first persisted.
     *
     * @return Performs a side effect by setting creation and update timestamps.
     */
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes the update timestamp before each row update.
     *
     * @return Performs a side effect by setting the update timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

