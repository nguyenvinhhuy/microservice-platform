package huynv.orderservice.context;

import java.util.Set;

public final class UserContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();

    /**
     * UserContext operation.
     *
     * @return performs side effects defined by this operation
     */
    private UserContext() {
    }

    /**
     * setTenantId operation.
     *
     * @param tenantId input parameter
     * @return performs side effects defined by this operation
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * getTenantId operation.
     *
     * @return getTenantId result
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * setUserId operation.
     *
     * @param userId input parameter
     * @return performs side effects defined by this operation
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * getUserId operation.
     *
     * @return getUserId result
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * setRoles operation.
     *
     * @param roles input parameter
     * @return performs side effects defined by this operation
     */
    public static void setRoles(Set<String> roles) {
        ROLES.set(roles);
    }

    /**
     * getRoles operation.
     *
     * @return getRoles result
     */
    public static Set<String> getRoles() {
        return ROLES.get();
    }

    /**
     * hasRole operation.
     *
     * @param role input parameter
     * @return hasRole result
     */
    public static boolean hasRole(String role) {
        Set<String> roles = ROLES.get();
        return roles != null && roles.contains(role);
    }

    /**
     * clear operation.
     *
     * @return performs side effects defined by this operation
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        ROLES.remove();
    }
}
