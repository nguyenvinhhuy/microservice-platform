package huynv.userservice.domain;

/**
 * Enumerates supported lifecycle states for a tenant-scoped user profile.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING,
    DELETED
}

