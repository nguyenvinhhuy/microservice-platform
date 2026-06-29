package huynv.userservice.domain;

/**
 * Enumerates supported tenant membership roles derived from Keycloak role claims.
 */
public enum MembershipRole {
    ROLE_USER,
    ROLE_ADMIN,
    ROLE_SUPPORT
}

