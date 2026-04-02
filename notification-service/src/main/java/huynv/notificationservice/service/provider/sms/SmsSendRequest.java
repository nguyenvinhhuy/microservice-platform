package huynv.notificationservice.service.provider.sms;

/**
 * Represents an SMS provider send request.
 *
 * @param to Recipient phone number.
 * @param body Rendered body content.
 */
public record SmsSendRequest(
        String to,
        String body
) {
}

