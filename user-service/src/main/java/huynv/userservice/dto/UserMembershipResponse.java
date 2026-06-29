package huynv.userservice.dto;

/**
 * Represents a tenant membership assigned to a user profile.
 *
 * @param role Membership role name.
 * @param status Membership lifecycle status.
 * @return Returns an immutable user-membership response.
 */
public record UserMembershipResponse(String role, String status) {
}

