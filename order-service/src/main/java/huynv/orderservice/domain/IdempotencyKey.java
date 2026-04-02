package huynv.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys",
        indexes = {
                @Index(name = "idx_idempotency_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_tenant_request_api", columnNames = {"tenant_id", "request_id", "api_name"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "api_name", nullable = false, length = 40)
    private String apiName;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Lob
    @Column(name = "response_payload")
    private String responsePayload;

    @PrePersist
    /**
     * prePersist operation.
     *
     * @return performs side effects defined by this operation
     */
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    /**
     * preUpdate operation.
     *
     * @return performs side effects defined by this operation
     */
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
