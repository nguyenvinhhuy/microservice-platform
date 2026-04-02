package huynv.productservice.context;

import java.util.List;

public class UserContext {
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private Long userId;
    private Long tenantId;
    private List<String> roles;

    /**
     * getCurrentUserContext operation.
     *
     * @return getCurrentUserContext result
     */
    public static UserContext getCurrentUserContext() {
        return CONTEXT.get();
    }

    /**
     * setCurrentUserContext operation.
     *
     * @param userContext input parameter
     * @return performs side effects defined by this operation
     */
    public static void setCurrentUserContext(UserContext userContext) {
        CONTEXT.set(userContext);
    }

    /**
     * clear operation.
     *
     * @return performs side effects defined by this operation
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * getUserId operation.
     *
     * @return getUserId result
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * setUserId operation.
     *
     * @param userId input parameter
     * @return performs side effects defined by this operation
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * getTenantId operation.
     *
     * @return getTenantId result
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * setTenantId operation.
     *
     * @param tenantId input parameter
     * @return performs side effects defined by this operation
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * getRoles operation.
     *
     * @return getRoles result
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * setRoles operation.
     *
     * @param roles input parameter
     * @return performs side effects defined by this operation
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
