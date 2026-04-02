package huynv.notificationservice.service.channel;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Delivers notifications through SMS using a provider integration placeholder.
 */
@Component
public class SmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    private final NotificationProperties properties;

    /**
     * Creates an SMS channel strategy controlled by configuration.
     *
     * @param properties Notification properties controlling SMS enablement.
     * @return Initializes an SMS channel strategy.
     */
    public SmsChannel(NotificationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.SMS;
    }

    @Override
    public boolean isEnabled() {
        return properties.getChannels().isSmsEnabled();
    }

    @Override
    public NotificationStatus send(NotificationMessage message) {
        Objects.requireNonNull(message, "message");
        if (!isEnabled()) {
            return NotificationStatus.SKIPPED;
        }
        log.info("SMS delivery simulated type={} tenantId={} userId={}", message.notificationType(), message.tenantId(), message.userId());
        return NotificationStatus.SENT;
    }
}

