package huynv.notificationservice.repository;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence operations for tenant-aware notification preference records.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /**
     * Loads all preference records for a user in a tenant.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preferences.
     * @return Returns all preferences for the user.
     */
    List<NotificationPreference> findByTenantIdAndUserId(Long tenantId, Long userId);

    /**
     * Loads a preference record for a specific channel.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preference.
     * @param channel Channel being configured.
     * @return Returns the preference when present.
     */
    Optional<NotificationPreference> findByTenantIdAndUserIdAndChannel(Long tenantId, Long userId, NotificationChannelType channel);
}

