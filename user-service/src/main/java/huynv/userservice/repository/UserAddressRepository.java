package huynv.userservice.repository;

import huynv.userservice.domain.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for user addresses.
 */
public interface UserAddressRepository extends JpaRepository<UserAddressEntity, UUID> {

    /**
     * Lists all addresses for a tenant-scoped user ordered by default flag and creation time.
     *
     * @param tenantId Tenant identifier owning the addresses.
     * @param userId Domain user identifier owning the addresses.
     * @return Returns all addresses belonging to the tenant-scoped user.
     */
    List<UserAddressEntity> findByTenantIdAndUserIdOrderByIsDefaultDescCreatedAtAsc(UUID tenantId, UUID userId);

    /**
     * Clears the default flag from all current default addresses for the tenant-scoped user.
     *
     * @param tenantId Tenant identifier owning the addresses.
     * @param userId Domain user identifier owning the addresses.
     * @return Returns the number of updated rows.
     */
    @Modifying
    @Query("update UserAddressEntity address set address.isDefault = false where address.tenantId = :tenantId and address.userId = :userId and address.isDefault = true")
    int clearDefaultAddress(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}

