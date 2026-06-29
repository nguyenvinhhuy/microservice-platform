package huynv.userservice.dto;

import java.util.List;

/**
 * Represents internal user contact data used by trusted platform services.
 *
 * @param email Email address stored for the user.
 * @param phoneNumber Phone number stored for the user.
 * @param pushTokens Push token list currently known for the user.
 * @return Returns an immutable internal contact response.
 */
public record InternalUserContactResponse(String email, String phoneNumber, List<String> pushTokens) {
}

