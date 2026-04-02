package huynv.notificationservice.service.provider.email;

/**
 * Represents an email provider send request.
 *
 * @param from Sender address.
 * @param to Recipient address.
 * @param subject Subject line.
 * @param body Rendered body content.
 */
public record EmailSendRequest(
        String from,
        String to,
        String subject,
        String body
) {
}

