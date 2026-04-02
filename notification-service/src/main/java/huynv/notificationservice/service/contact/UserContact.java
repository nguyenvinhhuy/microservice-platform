package huynv.notificationservice.service.contact;

import java.util.List;

/**
 * Represents contact information used for notification delivery.
 *
 * @param email Email address used for email delivery.
 * @param phoneNumber Phone number used for SMS delivery.
 * @param pushTokens Push tokens used for push notification delivery.
 */
public record UserContact(
        String email,
        String phoneNumber,
        List<String> pushTokens
) {
}

