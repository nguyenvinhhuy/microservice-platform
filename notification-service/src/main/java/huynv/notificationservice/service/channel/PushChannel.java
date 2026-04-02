package huynv.notificationservice.service.channel;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Delivers notifications through push messaging using a provider integration placeholder.
 */
@Component
public class PushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushChannel.class);

    private final NotificationProperties properties;

    /**
     * Creates a push notification channel strategy controlled by configuration.
     *
     * @param properties Notification properties controlling push enablement.
     * @return Initializes a push channel strategy.
     */
    public PushChannel(NotificationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public boolean isEnabled() {
        return properties.getChannels().isPushEnabled();
    }

    @Override
    public NotificationStatus send(NotificationMessage message) {
        Objects.requireNonNull(message, "message");
        if (!isEnabled()) {
            return NotificationStatus.SKIPPED;
        }
        log.info("Push delivery simulated type={} tenantId={} userId={}", message.notificationType(), message.tenantId(), message.userId());
        return NotificationStatus.SENT;
    }
}

