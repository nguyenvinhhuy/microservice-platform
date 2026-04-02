package huynv.notificationservice.security;

/**
 * Represents tenant and user identifiers extracted from a validated JWT for request authorization.
 *
 * @param tenantId Tenant identifier used for data isolation.
 * @param userId User identifier used for per-user access control.
 */
public record TenantUserContext(Long tenantId, Long userId) {
}

