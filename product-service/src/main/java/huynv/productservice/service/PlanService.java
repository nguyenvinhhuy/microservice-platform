package huynv.productservice.service;

import huynv.productservice.context.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanService {

    /**
     * Resolves the product creation quota for a given tenant.
     * In a real application, this would involve looking up the tenant's plan
     * from a database or another service.
     *
     * @param tenantId The ID of the tenant.
     * @return The product quota for the tenant's plan.
     */
    public int resolvePlanQuota(Long tenantId) {
        UserContext userContext = UserContext.getCurrentUserContext();
        if (userContext != null && userContext.getRoles() != null) {
            List<String> roles = userContext.getRoles();
            if (roles.contains("ROLE_PRO")) {
                return 10000; // PRO plan
            }
        }
        return 100; // FREE plan (default)
    }
}
