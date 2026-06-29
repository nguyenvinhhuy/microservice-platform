package huynv.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Persists tenant-scoped user notification and language preferences.
 */
@Entity
@Table(
        name = "user_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_preferences_tenant_user", columnNames = {"tenant_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_user_preferences_tenant_user", columnList = "tenant_id,user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferencesEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "marketing_enabled", nullable = false)
    private boolean marketingEnabled;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Creates a new preference row for a tenant-scoped user.
     *
     * @param id Preference row identifier.
     * @param tenantId Tenant identifier owning the preferences.
     * @param userId Domain user identifier owning the preferences.
     * @return Initializes a preference entity with conservative defaults.
     */
    public UserPreferencesEntity(UUID id, UUID tenantId, UUID userId) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.emailEnabled = true;
        this.smsEnabled = false;
        this.pushEnabled = false;
        this.marketingEnabled = false;
        this.language = "en";
    }

    /**
     * Applies mutable preference fields to the entity.
     *
     * @param emailEnabled Flag indicating whether email notifications are enabled.
     * @param smsEnabled Flag indicating whether SMS notifications are enabled.
     * @param pushEnabled Flag indicating whether push notifications are enabled.
     * @param marketingEnabled Flag indicating whether marketing notifications are enabled.
     * @param language Preferred language to persist.
     * @return Performs a side effect by mutating the entity state in memory.
     */
    public void apply(boolean emailEnabled, boolean smsEnabled, boolean pushEnabled, boolean marketingEnabled, String language) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.pushEnabled = pushEnabled;
        this.marketingEnabled = marketingEnabled;
        this.language = language;
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

