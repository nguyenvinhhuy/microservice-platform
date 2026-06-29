package huynv.userservice.mapper;

import huynv.userservice.domain.UserAddressEntity;
import huynv.userservice.domain.UserEntity;
import huynv.userservice.domain.UserMembershipEntity;
import huynv.userservice.domain.UserPreferencesEntity;
import huynv.userservice.dto.UserAddressResponse;
import huynv.userservice.dto.UserMembershipResponse;
import huynv.userservice.dto.UserPreferencesResponse;
import huynv.userservice.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Maps persistence entities to immutable API response records.
 */
@Component
public class UserApiMapper {

    /**
     * Maps a user entity and its memberships into a user profile response.
     *
     * @param userEntity Persisted user entity to expose.
     * @param memberships Membership rows belonging to the user.
     * @return Returns an immutable user profile response.
     */
    public UserProfileResponse toUserProfileResponse(UserEntity userEntity, List<UserMembershipEntity> memberships) {
        Objects.requireNonNull(userEntity, "userEntity");
        return new UserProfileResponse(
                userEntity.getId(),
                userEntity.getKeycloakUserId(),
                userEntity.getTenantId(),
                userEntity.getEmail(),
                userEntity.getFullName(),
                userEntity.getPhoneNumber(),
                userEntity.getAvatarUrl(),
                userEntity.getStatus().name(),
                userEntity.getLocale(),
                userEntity.getTimezone(),
                memberships == null ? List.of() : memberships.stream().map(this::toMembershipResponse).toList(),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt()
        );
    }

    /**
     * Maps a membership entity into a membership response.
     *
     * @param membershipEntity Persisted membership entity.
     * @return Returns an immutable membership response.
     */
    public UserMembershipResponse toMembershipResponse(UserMembershipEntity membershipEntity) {
        Objects.requireNonNull(membershipEntity, "membershipEntity");
        return new UserMembershipResponse(membershipEntity.getRole().name(), membershipEntity.getStatus().name());
    }

    /**
     * Maps a preferences entity into a preferences response.
     *
     * @param preferencesEntity Persisted preferences entity.
     * @return Returns an immutable preferences response.
     */
    public UserPreferencesResponse toPreferencesResponse(UserPreferencesEntity preferencesEntity) {
        Objects.requireNonNull(preferencesEntity, "preferencesEntity");
        return new UserPreferencesResponse(
                preferencesEntity.getId(),
                preferencesEntity.getUserId(),
                preferencesEntity.isEmailEnabled(),
                preferencesEntity.isSmsEnabled(),
                preferencesEntity.isPushEnabled(),
                preferencesEntity.isMarketingEnabled(),
                preferencesEntity.getLanguage(),
                preferencesEntity.getCreatedAt(),
                preferencesEntity.getUpdatedAt()
        );
    }

    /**
     * Maps an address entity into an address response.
     *
     * @param addressEntity Persisted address entity.
     * @return Returns an immutable address response.
     */
    public UserAddressResponse toAddressResponse(UserAddressEntity addressEntity) {
        Objects.requireNonNull(addressEntity, "addressEntity");
        return new UserAddressResponse(
                addressEntity.getId(),
                addressEntity.getLabel(),
                addressEntity.getCountry(),
                addressEntity.getCity(),
                addressEntity.getDistrict(),
                addressEntity.getAddressLine(),
                addressEntity.getPostalCode(),
                addressEntity.isDefault(),
                addressEntity.getCreatedAt(),
                addressEntity.getUpdatedAt()
        );
    }
}

