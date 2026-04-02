package huynv.notificationservice.service.provider.push;

import java.util.List;

/**
 * Represents a push provider send request.
 *
 * @param tokens Recipient push tokens.
 * @param body Rendered body content.
 */
public record PushSendRequest(
        List<String> tokens,
        String body
) {
}

