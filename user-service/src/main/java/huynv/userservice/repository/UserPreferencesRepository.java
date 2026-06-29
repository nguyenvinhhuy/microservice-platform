package huynv.userservice.repository;

import huynv.userservice.domain.UserPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for user preferences.
 */
public interface UserPreferencesRepository extends JpaRepository<UserPreferencesEntity, UUID> {

    /**
     * Finds preferences by tenant and domain user identifier.
     *
     * @param tenantId Tenant identifier owning the preferences.
     * @param userId Domain user identifier owning the preferences.
     * @return Returns the preference row when present.
     */
    Optional<UserPreferencesEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}

