package huynv.notificationservice.service;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationPreference;
import huynv.notificationservice.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Applies and manages tenant-aware per-user notification channel preferences.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationProperties properties;

    /**
     * Creates a preference service backed by the notification_preferences table.
     *
     * @param preferenceRepository Repository used to read and write notification preferences.
     * @param properties Notification properties used for global channel enablement flags.
     * @return Initializes a notification preference service.
     */
    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository, NotificationProperties properties) {
        this.preferenceRepository = Objects.requireNonNull(preferenceRepository, "preferenceRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Resolves enabled channels for a user, applying both global channel toggles and stored preferences.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preferences.
     * @return Returns the set of enabled channels for delivery.
     */
    @Transactional(readOnly = true)
    public EnumSet<NotificationChannelType> enabledChannels(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        List<NotificationPreference> prefs = preferenceRepository.findByTenantIdAndUserId(tenantId, userId);
        if (prefs == null || prefs.isEmpty()) {
            return defaultEnabledChannels();
        }

        EnumSet<NotificationChannelType> enabled = EnumSet.noneOf(NotificationChannelType.class);
        for (NotificationPreference pref : prefs) {
            if (pref == null || pref.getChannel() == null) {
                continue;
            }
            if (pref.isEnabled() && isGloballyEnabled(pref.getChannel())) {
                enabled.add(pref.getChannel());
            }
        }
        return enabled;
    }

    /**
     * Returns all stored preferences for a user.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preferences.
     * @return Returns the list of stored preferences.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreference> list(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        return preferenceRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    /**
     * Upserts a preference for a user and channel.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier owning the preferences.
     * @param channel Channel being configured.
     * @param enabled Whether delivery through the channel is enabled.
     * @return Persists the preference and returns the updated entity.
     */
    @Transactional
    public NotificationPreference upsert(Long tenantId, Long userId, NotificationChannelType channel, boolean enabled) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(channel, "channel");

        NotificationPreference pref = preferenceRepository
                .findByTenantIdAndUserIdAndChannel(tenantId, userId, channel)
                .orElseGet(() -> NotificationPreference.create(tenantId, userId, channel, enabled));
        pref.setEnabled(enabled);
        return preferenceRepository.save(pref);
    }

    private EnumSet<NotificationChannelType> defaultEnabledChannels() {
        EnumSet<NotificationChannelType> enabled = EnumSet.noneOf(NotificationChannelType.class);
        for (NotificationChannelType channel : NotificationChannelType.values()) {
            if (isGloballyEnabled(channel)) {
                enabled.add(channel);
            }
        }
        return enabled;
    }

    private boolean isGloballyEnabled(NotificationChannelType channel) {
        return switch (channel) {
            case EMAIL -> properties.getChannels().isEmailEnabled();
            case SMS -> properties.getChannels().isSmsEnabled();
            case PUSH -> properties.getChannels().isPushEnabled();
        };
    }
}


