package huynv.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a tenant-scoped user address.
 */
@Entity
@Table(
        name = "user_addresses",
        indexes = {
                @Index(name = "idx_user_addresses_tenant_user", columnList = "tenant_id,user_id"),
                @Index(name = "idx_user_addresses_tenant_user_default", columnList = "tenant_id,user_id,is_default")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddressEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label", length = 80)
    private String label;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "postal_code", length = 30)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Creates a new address for a tenant-scoped user.
     *
     * @param id Address identifier.
     * @param tenantId Tenant identifier owning the address.
     * @param userId Domain user identifier owning the address.
     * @return Initializes a new address entity.
     */
    public UserAddressEntity(UUID id, UUID tenantId, UUID userId) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    /**
     * Applies mutable address fields to the entity.
     *
     * @param label Human-readable address label.
     * @param country Country value to persist.
     * @param city City value to persist.
     * @param district District value to persist.
     * @param addressLine Address line to persist.
     * @param postalCode Postal code to persist.
     * @param isDefault Flag indicating whether the address is default.
     * @return Performs a side effect by mutating the entity state in memory.
     */
    public void apply(String label, String country, String city, String district, String addressLine, String postalCode, boolean isDefault) {
        this.label = label;
        this.country = country;
        this.city = city;
        this.district = district;
        this.addressLine = addressLine;
        this.postalCode = postalCode;
        this.isDefault = isDefault;
    }

    /**
     * Marks the entity timestamps when it is first persisted.
     *
     * @return Performs a side effect by initializing creation and update timestamps.
     */
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes the update timestamp before each database update.
     *
     * @return Performs a side effect by updating the last-modified timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

