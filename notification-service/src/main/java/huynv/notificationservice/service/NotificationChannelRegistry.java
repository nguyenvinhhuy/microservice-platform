package huynv.notificationservice.service;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.service.channel.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides a registry for notification channel strategies used by the dispatcher.
 */
@Component
public class NotificationChannelRegistry {

    private final Map<NotificationChannelType, NotificationChannel> channelsByType;

    /**
     * Creates a channel registry from discovered channel strategy beans.
     *
     * @param channels List of channel strategies detected in the Spring context.
     * @return Initializes a channel registry with type-based lookup.
     */
    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        Objects.requireNonNull(channels, "channels");
        EnumMap<NotificationChannelType, NotificationChannel> map = new EnumMap<>(NotificationChannelType.class);
        for (NotificationChannel channel : channels) {
            if (channel == null) {
                continue;
            }
            map.put(channel.channelType(), channel);
        }
        this.channelsByType = Map.copyOf(map);
    }

    /**
     * Returns a channel strategy for the given type when registered.
     *
     * @param channelType Channel type used for lookup.
     * @return Returns a channel strategy or null when not registered.
     */
    public NotificationChannel channel(NotificationChannelType channelType) {
        if (channelType == null) {
            return null;
        }
        return channelsByType.get(channelType);
    }
}

