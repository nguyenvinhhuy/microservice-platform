package huynv.inventoryservice.security;

public class UserContext {
    private static final ThreadLocal<Long> tenantId = new ThreadLocal<>();
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();

    /**
     * setTenantId operation.
     *
     * @param tenantId input parameter
     * @return performs side effects defined by this operation
     */
    public static void setTenantId(Long tenantId) {
        UserContext.tenantId.set(tenantId);
    }

    /**
     * getTenantId operation.
     *
     * @return getTenantId result
     */
    public static Long getTenantId() {
        return tenantId.get();
    }

    /**
     * setUserId operation.
     *
     * @param userId input parameter
     * @return performs side effects defined by this operation
     */
    public static void setUserId(Long userId) {
        UserContext.userId.set(userId);
    }

    /**
     * getUserId operation.
     *
     * @return getUserId result
     */
    public static Long getUserId() {
        return userId.get();
    }

    /**
     * clear operation.
     *
     * @return performs side effects defined by this operation
     */
    public static void clear() {
        tenantId.remove();
        userId.remove();
    }
}
