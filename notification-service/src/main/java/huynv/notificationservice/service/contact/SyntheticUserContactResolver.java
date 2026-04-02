package huynv.notificationservice.service.contact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides a deterministic fallback contact resolution strategy for environments without user-service.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "notification.synthetic-contacts", name = "enabled", havingValue = "true")
public class SyntheticUserContactResolver implements UserContactResolver {

    /**
     * Resolves a deterministic placeholder email address for the given user.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to resolve.
     * @return Returns a synthetic contact record suitable for non-production environments.
     */
    @Override
    public Optional<UserContact> resolve(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        return Optional.of(new UserContact("user-" + userId + "@example.local", null, List.of()));
    }
}
