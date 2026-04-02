package huynv.notificationservice.service.channel;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;

/**
 * Defines a strategy abstraction for delivering notifications over a specific channel.
 */
public interface NotificationChannel {

    /**
     * Returns the channel type supported by this implementation.
     *
     * @return Returns the supported NotificationChannelType.
     */
    NotificationChannelType channelType();

    /**
     * Returns whether the channel is enabled at runtime and should be used for delivery.
     *
     * @return Returns true when the channel is enabled for delivery.
     */
    boolean isEnabled();

    /**
     * Delivers a notification message using the channel implementation.
     *
     * @param message Normalized notification message to send.
     * @return Returns the delivery status indicating whether a message was sent or skipped.
     */
    NotificationStatus send(NotificationMessage message);
}
