package huynv.notificationservice.service;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.notificationservice.service.channel.NotificationChannel;
import huynv.notificationservice.service.channel.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that channel strategies are registered and resolved by channel type.
 */
public class NotificationChannelRegistryTest {

    /**
     * Resolves a registered channel by its declared type.
     *
     * @return Performs assertions on registry lookup behavior.
     */
    @Test
    public void shouldResolveChannelByType() {
        NotificationChannel email = new NotificationChannel() {
            @Override
            public NotificationChannelType channelType() {
                return NotificationChannelType.EMAIL;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public NotificationStatus send(NotificationMessage message) {
                return NotificationStatus.SENT;
            }
        };

        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of(email));
        assertThat(registry.channel(NotificationChannelType.EMAIL)).isNotNull();
        assertThat(registry.channel(NotificationChannelType.SMS)).isNull();
    }
}

