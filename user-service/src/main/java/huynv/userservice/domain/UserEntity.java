package huynv.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists the business profile owned by a tenant-scoped Keycloak subject.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_keycloak_user_id", columnNames = "keycloak_user_id")
        },
        indexes = {
                @Index(name = "idx_users_keycloak_user_id", columnList = "keycloak_user_id"),
                @Index(name = "idx_users_tenant_email", columnList = "tenant_id,email"),
                @Index(name = "idx_users_tenant_status", columnList = "tenant_id,status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    private UUID keycloakUserId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "email")
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "locale", length = 32)
    private String locale;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Creates a new tenant-scoped user profile linked to a Keycloak subject.
     *
     * @param id Domain user identifier.
     * @param keycloakUserId Keycloak subject identifier.
     * @param tenantId Tenant identifier owning the profile.
     * @return Initializes a new active user profile entity.
     */
    public UserEntity(UUID id, UUID keycloakUserId, UUID tenantId) {
        this.id = id;
        this.keycloakUserId = keycloakUserId;
        this.tenantId = tenantId;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Applies mutable profile fields while keeping the tenant and Keycloak linkage immutable.
     *
     * @param email Email address stored for the profile.
     * @param fullName Full display name stored for the profile.
     * @param phoneNumber Phone number stored for the profile.
     * @param avatarUrl Avatar URL stored for the profile.
     * @param status Lifecycle status to persist.
     * @param locale Preferred locale to persist.
     * @param timezone Preferred timezone to persist.
     * @return Performs a side effect by mutating the entity state in memory.
     */
    public void applyProfile(String email, String fullName, String phoneNumber, String avatarUrl, UserStatus status, String locale, String timezone) {
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.avatarUrl = avatarUrl;
        this.status = status == null ? UserStatus.ACTIVE : status;
        this.locale = locale;
        this.timezone = timezone;
    }

    /**
     * Marks the entity timestamps when it is first persisted.
     *
     * @return Performs a side effect by initializing creation and update timestamps.
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
     * Refreshes the update timestamp before each database update.
     *
     * @return Performs a side effect by updating the last-modified timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

