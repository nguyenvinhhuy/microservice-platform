package huynv.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a tenant-scoped role membership for a user profile.
 */
@Entity
@Table(
        name = "user_memberships",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_memberships_tenant_user_role", columnNames = {"tenant_id", "user_id", "role"})
        },
        indexes = {
                @Index(name = "idx_user_memberships_tenant_user", columnList = "tenant_id,user_id"),
                @Index(name = "idx_user_memberships_tenant_role", columnList = "tenant_id,role,status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMembershipEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Creates a membership row for a tenant-scoped user role.
     *
     * @param id Membership identifier.
     * @param tenantId Tenant identifier owning the membership.
     * @param userId Domain user identifier owning the membership.
     * @param role Role assigned to the user for the tenant.
     * @return Initializes a new active membership entity.
     */
    public UserMembershipEntity(UUID id, UUID tenantId, UUID userId, MembershipRole role) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    /**
     * Updates the membership status.
     *
     * @param status Membership status to persist.
     * @return Performs a side effect by mutating the entity state in memory.
     */
    public void setMembershipStatus(MembershipStatus status) {
        this.status = status;
    }

    /**
     * Marks the entity timestamp when it is first persisted.
     *
     * @return Performs a side effect by initializing creation timestamp and identifier.
     */
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = MembershipStatus.ACTIVE;
        }
    }
}

