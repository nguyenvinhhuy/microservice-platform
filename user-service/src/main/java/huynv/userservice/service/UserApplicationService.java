package huynv.userservice.service;

import huynv.userservice.cache.UserQueryCacheService;
import huynv.userservice.domain.MembershipRole;
import huynv.userservice.domain.MembershipStatus;
import huynv.userservice.domain.UserAddressEntity;
import huynv.userservice.domain.UserEntity;
import huynv.userservice.domain.UserMembershipEntity;
import huynv.userservice.domain.UserPreferencesEntity;
import huynv.userservice.domain.UserStatus;
import huynv.userservice.dto.CreateUserAddressRequest;
import huynv.userservice.dto.InternalUserContactResponse;
import huynv.userservice.dto.PagedUsersResponse;
import huynv.userservice.dto.UpdateUserPreferencesRequest;
import huynv.userservice.dto.UpdateUserProfileRequest;
import huynv.userservice.dto.UserAddressResponse;
import huynv.userservice.dto.UserPreferencesResponse;
import huynv.userservice.dto.UserProfileResponse;
import huynv.userservice.event.UserEventPublisher;
import huynv.userservice.exception.ForbiddenOperationException;
import huynv.userservice.exception.ResourceNotFoundException;
import huynv.userservice.mapper.UserApiMapper;
import huynv.userservice.metrics.UserMetrics;
import huynv.userservice.repository.UserAddressRepository;
import huynv.userservice.repository.UserMembershipRepository;
import huynv.userservice.repository.UserPreferencesRepository;
import huynv.userservice.repository.UserRepository;
import huynv.userservice.security.AuthenticatedUser;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the tenant-aware user-service application use cases for profile, preferences, address, and lookup APIs.
 */
@Service
@Observed(name = "user.service")
public class UserApplicationService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final UserQueryCacheService userQueryCacheService;
    private final UserApiMapper userApiMapper;
    private final UserEventPublisher userEventPublisher;
    private final UserMetrics userMetrics;

    /**
     * Creates the application service coordinating persistence, caching, metrics, and event publication.
     *
     * @param userRepository Repository used for tenant-scoped user profiles.
     * @param userPreferencesRepository Repository used for tenant-scoped preferences.
     * @param userAddressRepository Repository used for tenant-scoped addresses.
     * @param userMembershipRepository Repository used for tenant-scoped memberships.
     * @param userQueryCacheService Cache-backed query service for profiles and preferences.
     * @param userApiMapper Mapper used to build immutable API responses.
     * @param userEventPublisher Event publisher used to persist outbox events.
     * @param userMetrics Metrics recorder used for user lookup and update metrics.
     * @return Initializes a user application service instance.
     */
    public UserApplicationService(
        UserRepository userRepository,
        UserPreferencesRepository userPreferencesRepository,
        UserAddressRepository userAddressRepository,
        UserMembershipRepository userMembershipRepository,
        UserQueryCacheService userQueryCacheService,
        UserApiMapper userApiMapper,
        UserEventPublisher userEventPublisher,
        UserMetrics userMetrics
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.userPreferencesRepository = Objects.requireNonNull(userPreferencesRepository, "userPreferencesRepository");
        this.userAddressRepository = Objects.requireNonNull(userAddressRepository, "userAddressRepository");
        this.userMembershipRepository = Objects.requireNonNull(userMembershipRepository, "userMembershipRepository");
        this.userQueryCacheService = Objects.requireNonNull(userQueryCacheService, "userQueryCacheService");
        this.userApiMapper = Objects.requireNonNull(userApiMapper, "userApiMapper");
        this.userEventPublisher = Objects.requireNonNull(userEventPublisher, "userEventPublisher");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Returns the current user profile response.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(AuthenticatedUser authenticatedUser) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        long startedAt = System.nanoTime();
        try {
            UserQueryCacheService.CachedUserProfile cachedProfile = userQueryCacheService
                .findProfileByKeycloakUserId(authenticatedUser.tenantId(), authenticatedUser.userId())
                .orElseThrow(() ->
                    new ResourceNotFoundException("USER_NOT_FOUND", "The current user profile does not exist.")
                );
            return toProfileResponse(cachedProfile, memberships(cachedProfile.id(), authenticatedUser.tenantId()));
        } finally {
            userMetrics.recordLookup(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    /**
     * Creates or updates the current authenticated user profile.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param request Mutable profile fields submitted by the caller.
     * @param correlationId Correlation identifier for event publication.
     * @param causationId Causation identifier for event publication.
     * @return Returns the persisted user profile response.
     */
    @Transactional
    public UserProfileResponse updateCurrentUserProfile(
        AuthenticatedUser authenticatedUser,
        UpdateUserProfileRequest request,
        String correlationId,
        String causationId
    ) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(request, "request");

        UserMutationTarget mutationTarget = prepareUserMutationTarget(authenticatedUser);
        validateRequestedStatus(authenticatedUser, request.status());
        mutationTarget
            .userEntity()
            .applyProfile(
                trim(request.email()),
                trim(request.fullName()),
                trim(request.phoneNumber()),
                trim(request.avatarUrl()),
                request.status() == null ? UserStatus.ACTIVE : request.status(),
                trim(request.locale()),
                trim(request.timezone())
            );
        UserEntity savedUser = userRepository.save(mutationTarget.userEntity());
        synchronizeMemberships(savedUser, authenticatedUser.roles());
        UserPreferencesEntity preferencesEntity = ensurePreferences(savedUser);
        evictCaches(savedUser, preferencesEntity);
        publishProfileEvent(savedUser, mutationTarget.created(), correlationId, causationId);
        userMetrics.recordProfileUpdate();
        return userApiMapper.toUserProfileResponse(savedUser, memberships(savedUser.getId(), savedUser.getTenantId()));
    }

    /**
     * Returns a tenant-scoped user profile by domain identifier, enforcing self-or-privileged access.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param userId Domain user identifier to load.
     * @return Returns the requested user profile response.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(AuthenticatedUser authenticatedUser, UUID userId) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(userId, "userId");
        long startedAt = System.nanoTime();
        try {
            UserQueryCacheService.CachedUserProfile targetProfile = userQueryCacheService
                .findProfileById(authenticatedUser.tenantId(), userId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("USER_NOT_FOUND", "The requested user profile does not exist.")
                );
            enforceSelfOrPrivilegedAccess(authenticatedUser, targetProfile.id());
            return toProfileResponse(targetProfile, memberships(targetProfile.id(), authenticatedUser.tenantId()));
        } finally {
            userMetrics.recordLookup(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    /**
     * Searches tenant users using optional email, status, and role filters.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param email Optional email substring filter.
     * @param status Optional lifecycle status filter.
     * @param role Optional membership role filter.
     * @param page Zero-based page index.
     * @param size Requested page size.
     * @param maxPageSize Maximum page size permitted by configuration.
     * @return Returns a paginated tenant user search response.
     */
    @Transactional(readOnly = true)
    public PagedUsersResponse searchUsers(
        AuthenticatedUser authenticatedUser,
        String email,
        UserStatus status,
        MembershipRole role,
        int page,
        int size,
        int maxPageSize
    ) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        enforcePrivilegedAccess(authenticatedUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, maxPageSize));
        long startedAt = System.nanoTime();
        try {
            Page<UserEntity> resultPage = userRepository.searchTenantUsers(
                authenticatedUser.tenantId(),
                escapeLikePattern(blankToNull(email)),
                status,
                role,
                MembershipStatus.ACTIVE,
                PageRequest.of(safePage, safeSize)
            );
            Map<UUID, List<UserMembershipEntity>> membershipsByUserId = membershipsByUserId(
                authenticatedUser.tenantId(),
                resultPage.getContent()
            );
            List<UserProfileResponse> content = resultPage
                .getContent()
                .stream()
                .map(userEntity ->
                    userApiMapper.toUserProfileResponse(
                        userEntity,
                        membershipsByUserId.getOrDefault(userEntity.getId(), List.of())
                    )
                )
                .toList();
            return new PagedUsersResponse(
                content,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
            );
        } finally {
            userMetrics.recordLookup(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    /**
     * Returns preferences for the currently authenticated user.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Returns the current user's preference response.
     */
    @Transactional(readOnly = true)
    public UserPreferencesResponse getCurrentUserPreferences(AuthenticatedUser authenticatedUser) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        long startedAt = System.nanoTime();
        try {
            UserQueryCacheService.CachedUserProfile profile = currentProfileOrThrow(authenticatedUser);
            return userQueryCacheService
                .findPreferences(authenticatedUser.tenantId(), profile.id())
                .map(this::toPreferencesResponse)
                .orElseGet(() ->
                    new UserPreferencesResponse(null, profile.id(), true, false, false, false, "en", null, null)
                );
        } finally {
            userMetrics.recordLookup(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    /**
     * Creates or updates preferences for the currently authenticated user.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param request Mutable preferences submitted by the caller.
     * @param correlationId Correlation identifier for event publication.
     * @param causationId Causation identifier for event publication.
     * @return Returns the persisted preference response.
     */
    @Transactional
    public UserPreferencesResponse updateCurrentUserPreferences(
        AuthenticatedUser authenticatedUser,
        UpdateUserPreferencesRequest request,
        String correlationId,
        String causationId
    ) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(request, "request");
        UserMutationTarget mutationTarget = prepareUserMutationTarget(authenticatedUser);
        synchronizeMemberships(mutationTarget.userEntity(), authenticatedUser.roles());
        UserPreferencesEntity preferencesEntity = userPreferencesRepository
            .findByTenantIdAndUserId(mutationTarget.userEntity().getTenantId(), mutationTarget.userEntity().getId())
            .orElseGet(() ->
                new UserPreferencesEntity(
                    UUID.randomUUID(),
                    mutationTarget.userEntity().getTenantId(),
                    mutationTarget.userEntity().getId()
                )
            );
        preferencesEntity.apply(
            request.emailEnabled(),
            request.smsEnabled(),
            request.pushEnabled(),
            request.marketingEnabled(),
            defaultLanguage(request.language())
        );
        UserPreferencesEntity savedPreferences = userPreferencesRepository.save(preferencesEntity);
        evictCaches(mutationTarget.userEntity(), savedPreferences);
        if (mutationTarget.created()) {
            userEventPublisher.publishUserCreated(mutationTarget.userEntity(), correlationId, causationId);
        }
        userEventPublisher.publishPreferencesUpdated(savedPreferences, correlationId, causationId);
        return userApiMapper.toPreferencesResponse(savedPreferences);
    }

    /**
     * Lists addresses for the currently authenticated user.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Returns a list of current user addresses.
     */
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getCurrentUserAddresses(AuthenticatedUser authenticatedUser) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        long startedAt = System.nanoTime();
        try {
            UserQueryCacheService.CachedUserProfile profile = currentProfileOrThrow(authenticatedUser);
            return userAddressRepository
                .findByTenantIdAndUserIdOrderByIsDefaultDescCreatedAtAsc(authenticatedUser.tenantId(), profile.id())
                .stream()
                .map(userApiMapper::toAddressResponse)
                .toList();
        } finally {
            userMetrics.recordLookup(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    /**
     * Creates a new address for the currently authenticated user.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param request Mutable address fields submitted by the caller.
     * @param correlationId Correlation identifier for event publication.
     * @param causationId Causation identifier for event publication.
     * @return Returns the persisted address response.
     */
    @Transactional
    public UserAddressResponse createCurrentUserAddress(
        AuthenticatedUser authenticatedUser,
        CreateUserAddressRequest request,
        String correlationId,
        String causationId
    ) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(request, "request");
        UserMutationTarget mutationTarget = prepareUserMutationTarget(authenticatedUser);
        synchronizeMemberships(mutationTarget.userEntity(), authenticatedUser.roles());
        if (request.isDefault()) {
            clearExistingDefaultAddresses(
                mutationTarget.userEntity().getTenantId(),
                mutationTarget.userEntity().getId()
            );
        }
        UserAddressEntity addressEntity = new UserAddressEntity(
            UUID.randomUUID(),
            mutationTarget.userEntity().getTenantId(),
            mutationTarget.userEntity().getId()
        );
        addressEntity.apply(
            trim(request.label()),
            trim(request.country()),
            trim(request.city()),
            trim(request.district()),
            trim(request.addressLine()),
            trim(request.postalCode()),
            request.isDefault()
        );
        UserAddressEntity savedAddress = userAddressRepository.save(addressEntity);
        if (mutationTarget.created()) {
            userEventPublisher.publishUserCreated(mutationTarget.userEntity(), correlationId, causationId);
        }
        userEventPublisher.publishAddressCreated(savedAddress, correlationId, causationId);
        return userApiMapper.toAddressResponse(savedAddress);
    }

    /**
     * Returns trusted internal contact data for a tenant-scoped user.
     *
     * @param tenantId Tenant identifier that scopes the internal lookup.
     * @param userId Domain user identifier to resolve.
     * @return Returns the internal contact response for the requested user.
     */
    @Transactional(readOnly = true)
    public InternalUserContactResponse getInternalUserContact(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        UserQueryCacheService.CachedUserProfile profile = userQueryCacheService
            .findProfileById(tenantId, userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("USER_NOT_FOUND", "The requested user profile does not exist.")
            );
        return new InternalUserContactResponse(profile.email(), profile.phoneNumber(), List.of());
    }

    /**
     * Returns the current user's cached profile snapshot or fails when no profile exists.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Returns the current user's cached profile snapshot.
     */
    private UserQueryCacheService.CachedUserProfile currentProfileOrThrow(AuthenticatedUser authenticatedUser) {
        return userQueryCacheService
            .findProfileByKeycloakUserId(authenticatedUser.tenantId(), authenticatedUser.userId())
            .orElseThrow(() ->
                new ResourceNotFoundException("USER_NOT_FOUND", "The current user profile does not exist.")
            );
    }

    /**
     * Creates a mutation target by loading or creating the current user profile entity.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Returns a mutation target containing the user entity and a created flag.
     */
    private UserMutationTarget prepareUserMutationTarget(AuthenticatedUser authenticatedUser) {
        Optional<UserEntity> existingUser = userRepository.findByTenantIdAndKeycloakUserIdAndDeletedAtIsNull(
            authenticatedUser.tenantId(),
            authenticatedUser.userId()
        );
        if (existingUser.isPresent()) {
            return new UserMutationTarget(existingUser.get(), false);
        }
        UserEntity userEntity = new UserEntity(
            UUID.randomUUID(),
            authenticatedUser.userId(),
            authenticatedUser.tenantId()
        );
        UserEntity savedUser = userRepository.save(userEntity);
        ensurePreferences(savedUser);
        return new UserMutationTarget(savedUser, true);
    }

    /**
     * Synchronizes persisted tenant memberships with the roles present in the validated JWT.
     *
     * @param userEntity Persisted user entity owning the memberships.
     * @param roles Supported role names extracted from JWT claims.
     * @return Performs side effects by inserting or updating membership rows.
     */
    private void synchronizeMemberships(UserEntity userEntity, Set<String> roles) {
        List<UserMembershipEntity> existingMemberships = userMembershipRepository.findByTenantIdAndUserId(
            userEntity.getTenantId(),
            userEntity.getId()
        );
        Set<MembershipRole> desiredRoles = desiredRoles(roles);
        List<UserMembershipEntity> mutatedMemberships = new ArrayList<>(existingMemberships);
        for (MembershipRole desiredRole : desiredRoles) {
            UserMembershipEntity membershipEntity = existingMemberships
                .stream()
                .filter(existing -> existing.getRole() == desiredRole)
                .findFirst()
                .orElseGet(() -> {
                    UserMembershipEntity createdMembership = new UserMembershipEntity(
                        UUID.randomUUID(),
                        userEntity.getTenantId(),
                        userEntity.getId(),
                        desiredRole
                    );
                    mutatedMemberships.add(createdMembership);
                    return createdMembership;
                });
            membershipEntity.setMembershipStatus(MembershipStatus.ACTIVE);
        }
        for (UserMembershipEntity existingMembership : mutatedMemberships) {
            if (!desiredRoles.contains(existingMembership.getRole())) {
                existingMembership.setMembershipStatus(MembershipStatus.INACTIVE);
            }
        }
        userMembershipRepository.saveAll(mutatedMemberships);
    }

    /**
     * Ensures a default preferences row exists for a tenant-scoped user.
     *
     * @param userEntity Persisted user entity owning the preferences.
     * @return Returns the existing or newly created preferences entity.
     */
    private UserPreferencesEntity ensurePreferences(UserEntity userEntity) {
        return userPreferencesRepository
            .findByTenantIdAndUserId(userEntity.getTenantId(), userEntity.getId())
            .orElseGet(() ->
                userPreferencesRepository.save(
                    new UserPreferencesEntity(UUID.randomUUID(), userEntity.getTenantId(), userEntity.getId())
                )
            );
    }

    /**
     * Clears the default flag from any currently default addresses for the provided user.
     *
     * @param tenantId Tenant identifier owning the addresses.
     * @param userId Domain user identifier owning the addresses.
     * @return Performs side effects by updating persisted default-address state.
     */
    private void clearExistingDefaultAddresses(UUID tenantId, UUID userId) {
        userAddressRepository.clearDefaultAddress(tenantId, userId);
    }

    /**
     * Publishes the correct profile event for a create-or-update mutation.
     *
     * @param userEntity Persisted user entity that was mutated.
     * @param created Flag indicating whether the profile was newly created.
     * @param correlationId Correlation identifier for event publication.
     * @param causationId Causation identifier for event publication.
     * @return Performs side effects by persisting the correct user profile event to the outbox.
     */
    private void publishProfileEvent(UserEntity userEntity, boolean created, String correlationId, String causationId) {
        if (created) {
            userEventPublisher.publishUserCreated(userEntity, correlationId, causationId);
            return;
        }
        userEventPublisher.publishUserUpdated(userEntity, correlationId, causationId);
    }

    /**
     * Evicts cached profile and preferences entries after a mutation succeeds.
     *
     * @param userEntity Persisted user entity whose caches must be evicted.
     * @param preferencesEntity Persisted preferences entity whose caches must be evicted.
     * @return Performs side effects by clearing Redis cache entries.
     */
    private void evictCaches(UserEntity userEntity, UserPreferencesEntity preferencesEntity) {
        userQueryCacheService.evictUserProfile(
            userEntity.getTenantId(),
            userEntity.getId(),
            userEntity.getKeycloakUserId()
        );
        userQueryCacheService.evictPreferences(preferencesEntity.getTenantId(), preferencesEntity.getUserId());
    }

    /**
     * Validates whether the requested profile status may be set by the current caller.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param requestedStatus Requested profile status.
     * @return Performs a side effect by throwing when an unsupported status change is requested.
     */
    private void validateRequestedStatus(AuthenticatedUser authenticatedUser, UserStatus requestedStatus) {
        if (requestedStatus == null || authenticatedUser.isAdminOrSupport()) {
            return;
        }
        if (requestedStatus != UserStatus.ACTIVE && requestedStatus != UserStatus.PENDING) {
            throw new ForbiddenOperationException(
                "STATUS_CHANGE_FORBIDDEN",
                "Only administrators or support users may set this profile status."
            );
        }
    }

    /**
     * Enforces that only administrators or support users may execute the current operation.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @return Performs a side effect by throwing when the caller lacks privileged roles.
     */
    private void enforcePrivilegedAccess(AuthenticatedUser authenticatedUser) {
        if (!authenticatedUser.isAdminOrSupport()) {
            throw new ForbiddenOperationException(
                "ACCESS_FORBIDDEN",
                "Only administrators or support users may access this resource."
            );
        }
    }

    /**
     * Enforces that the caller is either privileged or is requesting their own profile.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param userId Domain user identifier being accessed.
     * @return Performs a side effect by throwing when the caller is not allowed to access the requested user.
     */
    private void enforceSelfOrPrivilegedAccess(AuthenticatedUser authenticatedUser, UUID userId) {
        if (authenticatedUser.isAdminOrSupport()) {
            return;
        }
        UserQueryCacheService.CachedUserProfile currentProfile = currentProfileOrThrow(authenticatedUser);
        if (!currentProfile.id().equals(userId)) {
            throw new ForbiddenOperationException("ACCESS_FORBIDDEN", "You may only access your own profile.");
        }
    }

    /**
     * Returns memberships for a tenant-scoped user sorted deterministically by role.
     *
     * @param userId Domain user identifier owning the memberships.
     * @param tenantId Tenant identifier owning the memberships.
     * @return Returns sorted membership rows for the tenant-scoped user.
     */
    private List<UserMembershipEntity> memberships(UUID userId, UUID tenantId) {
        return userMembershipRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .stream()
            .sorted(Comparator.comparing(membership -> membership.getRole().name()))
            .toList();
    }

    /**
     * Loads memberships for a page of users in one query and groups them by owning user identifier.
     *
     * @param tenantId Tenant identifier owning the memberships.
     * @param users Tenant-scoped users returned by the search query.
     * @return Returns grouped membership rows keyed by user identifier.
     */
    private Map<UUID, List<UserMembershipEntity>> membershipsByUserId(UUID tenantId, List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Set<UUID> userIds = users.stream().map(UserEntity::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<UserMembershipEntity>> groupedMemberships = new HashMap<>();
        for (UserMembershipEntity membershipEntity : userMembershipRepository.findByTenantIdAndUserIdIn(tenantId, userIds)) {
            groupedMemberships.computeIfAbsent(membershipEntity.getUserId(), ignored -> new ArrayList<>()).add(membershipEntity);
        }
        groupedMemberships.values().forEach(memberships -> memberships.sort(Comparator.comparing(membership -> membership.getRole().name())));
        return groupedMemberships;
    }

    /**
     * Builds a profile response from a cached profile snapshot and live membership rows.
     *
     * @param cachedProfile Cache-safe profile snapshot.
     * @param memberships Membership rows to include in the response.
     * @return Returns an immutable user profile response.
     */
    private UserProfileResponse toProfileResponse(
        UserQueryCacheService.CachedUserProfile cachedProfile,
        List<UserMembershipEntity> memberships
    ) {
        return new UserProfileResponse(
            cachedProfile.id(),
            cachedProfile.keycloakUserId(),
            cachedProfile.tenantId(),
            cachedProfile.email(),
            cachedProfile.fullName(),
            cachedProfile.phoneNumber(),
            cachedProfile.avatarUrl(),
            cachedProfile.status(),
            cachedProfile.locale(),
            cachedProfile.timezone(),
            memberships.stream().map(userApiMapper::toMembershipResponse).toList(),
            cachedProfile.createdAt(),
            cachedProfile.updatedAt()
        );
    }

    /**
     * Maps a cached preference snapshot into the public preference response.
     *
     * @param cachedUserPreferences Cache-safe preference snapshot.
     * @return Returns an immutable user-preferences response.
     */
    private UserPreferencesResponse toPreferencesResponse(
        UserQueryCacheService.CachedUserPreferences cachedUserPreferences
    ) {
        return new UserPreferencesResponse(
            cachedUserPreferences.id(),
            cachedUserPreferences.userId(),
            cachedUserPreferences.emailEnabled(),
            cachedUserPreferences.smsEnabled(),
            cachedUserPreferences.pushEnabled(),
            cachedUserPreferences.marketingEnabled(),
            cachedUserPreferences.language(),
            cachedUserPreferences.createdAt(),
            cachedUserPreferences.updatedAt()
        );
    }

    /**
     * Maps the caller's JWT role names into persisted membership roles.
     *
     * @param roleNames Supported role names extracted from JWT claims.
     * @return Returns a deterministic set of desired membership roles.
     */
    private Set<MembershipRole> desiredRoles(Set<String> roleNames) {
        Set<MembershipRole> desiredRoles = new LinkedHashSet<>();
        if (roleNames != null) {
            for (String roleName : roleNames) {
                desiredRoles.add(MembershipRole.valueOf(roleName));
            }
        }
        if (desiredRoles.isEmpty()) {
            desiredRoles.add(MembershipRole.ROLE_USER);
        }
        return desiredRoles;
    }

    /**
     * Normalizes a preference language value to a deterministic default when blank.
     *
     * @param language Candidate language value supplied by the caller.
     * @return Returns the provided language when present, or en when blank.
     */
    private String defaultLanguage(String language) {
        return language == null || language.isBlank() ? "en" : language.trim();
    }

    /**
     * Trims a candidate string value and converts blank strings to null.
     *
     * @param value Candidate string value.
     * @return Returns the trimmed string value, or null when blank.
     */
    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Converts blank filter values to null so optional repository filters remain disabled.
     *
     * @param value Candidate filter value.
     * @return Returns the trimmed value, or null when blank.
     */
    private String blankToNull(String value) {
        return trim(value);
    }

    /**
     * Escapes LIKE metacharacters in a filter value so they are treated as literals by the ESCAPE '!' clause.
     *
     * @param value Pre-trimmed LIKE pattern value, or null.
     * @return Returns the escaped value, or null when the input is null.
     */
    private String escapeLikePattern(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * Represents a mutable user target resolved for a command operation.
     *
     * @param userEntity Persisted user entity to mutate.
     * @param created Flag indicating whether the entity was newly created for the command.
     * @return Returns an immutable mutation target descriptor.
     */
    private record UserMutationTarget(UserEntity userEntity, boolean created) {}
}
