package huynv.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores per-user notification channel preferences for tenant-aware delivery filtering.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannelType channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationPreference() {
    }

    /**
     * Creates a new notification preference entity.
     *
     * @param id Preference identifier.
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preference.
     * @param channel Channel being configured.
     * @param enabled Whether delivery through the channel is enabled.
     * @param updatedAt Timestamp when the preference was last updated.
     * @return Returns a fully initialized NotificationPreference entity.
     */
    public NotificationPreference(UUID id,
                                  Long tenantId,
                                  Long userId,
                                  NotificationChannelType channel,
                                  boolean enabled,
                                  OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.enabled = enabled;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Creates a new preference with generated identifiers and timestamp.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preference.
     * @param channel Channel being configured.
     * @param enabled Whether delivery through the channel is enabled.
     * @return Returns a new NotificationPreference entity.
     */
    public static NotificationPreference create(Long tenantId,
                                                Long userId,
                                                NotificationChannelType channel,
                                                boolean enabled) {
        return new NotificationPreference(UUID.randomUUID(), tenantId, userId, channel, enabled, OffsetDateTime.now());
    }

    /**
     * Updates the enabled flag and refreshed timestamp.
     *
     * @param enabled Whether delivery through the channel is enabled.
     * @return Performs a side effect by mutating the preference state.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Returns the preference identifier.
     *
     * @return Returns the preference identifier.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the tenant identifier used for data isolation.
     *
     * @return Returns the tenant identifier.
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * Returns the user identifier owning the preference.
     *
     * @return Returns the user identifier.
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Returns the channel type configured by this preference.
     *
     * @return Returns the channel type.
     */
    public NotificationChannelType getChannel() {
        return channel;
    }

    /**
     * Returns whether this channel is enabled for the user.
     *
     * @return Returns true when enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the timestamp when the preference was last updated.
     *
     * @return Returns the updatedAt timestamp.
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

