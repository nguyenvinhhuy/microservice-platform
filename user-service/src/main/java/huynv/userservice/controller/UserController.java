package huynv.userservice.controller;

import huynv.userservice.config.UserServiceProperties;
import huynv.userservice.domain.MembershipRole;
import huynv.userservice.domain.UserStatus;
import huynv.userservice.dto.CreateUserAddressRequest;
import huynv.userservice.dto.PagedUsersResponse;
import huynv.userservice.dto.UpdateUserPreferencesRequest;
import huynv.userservice.dto.UpdateUserProfileRequest;
import huynv.userservice.dto.UserAddressResponse;
import huynv.userservice.dto.UserPreferencesResponse;
import huynv.userservice.dto.UserProfileResponse;
import huynv.userservice.security.AuthenticatedUser;
import huynv.userservice.security.JwtUserContextExtractor;
import huynv.userservice.service.ApiIdempotencyService;
import huynv.userservice.service.UserApplicationService;
import io.micrometer.observation.annotation.Observed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;

/**
 * Exposes the tenant-aware public REST API for user profile, preference, address, and lookup operations.
 */
@RestController
@Validated
@RequestMapping("/users")
@Observed(name = "user.api")
public class UserController {

    private static final String UPDATE_PROFILE_OPERATION = "PUT:/users/me";
    private static final String UPDATE_PREFERENCES_OPERATION = "PUT:/users/preferences";
    private static final String CREATE_ADDRESS_OPERATION = "POST:/users/addresses";

    private final UserApplicationService userApplicationService;
    private final JwtUserContextExtractor jwtUserContextExtractor;
    private final UserServiceProperties properties;
    private final ApiIdempotencyService apiIdempotencyService;

    /**
     * Creates a public user API controller.
     *
     * @param userApplicationService Application service implementing user use cases.
     * @param jwtUserContextExtractor JWT extractor used to resolve tenant-scoped caller identity.
     * @param properties User-service configuration properties.
     * @param apiIdempotencyService Service used to persist and replay idempotent write responses.
     * @return Initializes a public user API controller instance.
     */
    public UserController(
            UserApplicationService userApplicationService,
            JwtUserContextExtractor jwtUserContextExtractor,
            UserServiceProperties properties,
            ApiIdempotencyService apiIdempotencyService
    ) {
        this.userApplicationService = Objects.requireNonNull(userApplicationService, "userApplicationService");
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.apiIdempotencyService = Objects.requireNonNull(apiIdempotencyService, "apiIdempotencyService");
    }

    /**
     * Returns the current authenticated user's profile.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @return Returns the current user profile response.
     */
    @GetMapping("/me")
    public UserProfileResponse getCurrentUserProfile(Authentication authentication) {
        return userApplicationService.getCurrentUserProfile(currentUser(authentication));
    }

    /**
     * Creates or updates the current authenticated user's profile.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @param request Mutable profile request body.
     * @param idempotencyKey Required idempotency header used to safely replay retries.
     * @param correlationId Optional correlation identifier header.
     * @param causationId Optional causation identifier header.
     * @param httpServletRequest Current HTTP request used for request-id fallback.
     * @return Returns the persisted user profile response.
     */
    @PutMapping("/me")
    public UserProfileResponse updateCurrentUserProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = currentUser(authentication);
        String resolvedCorrelationId = resolveCorrelationId(correlationId, httpServletRequest);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        return apiIdempotencyService.execute(
                authenticatedUser,
                UPDATE_PROFILE_OPERATION,
                idempotencyKey,
                request,
                UserProfileResponse.class,
                200,
                () -> userApplicationService.updateCurrentUserProfile(authenticatedUser, request, resolvedCorrelationId, resolvedCausationId)
        );
    }

    /**
     * Returns a tenant-scoped user profile by domain identifier.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @param userId Domain user identifier to load.
     * @return Returns the requested user profile response.
     */
    @GetMapping("/{userId}")
    public UserProfileResponse getUserById(Authentication authentication, @PathVariable UUID userId) {
        return userApplicationService.getUserById(currentUser(authentication), userId);
    }

    /**
     * Searches tenant users with optional email, status, and role filters.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @param email Optional email substring filter.
     * @param status Optional user status filter.
     * @param role Optional membership role filter.
     * @param page Zero-based page index.
     * @param size Requested page size.
     * @return Returns a paginated tenant user search response.
     */
    @GetMapping
    public PagedUsersResponse searchUsers(
            Authentication authentication,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) MembershipRole role,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be zero or greater.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be greater than zero.") @Max(value = 500, message = "Size must not exceed 500.") int size
    ) {
        return userApplicationService.searchUsers(currentUser(authentication), email, status, role, page, size, properties.getMaxPageSize());
    }

    /**
     * Returns preferences for the current authenticated user.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @return Returns the current user's preference response.
     */
    @GetMapping("/preferences")
    public UserPreferencesResponse getCurrentUserPreferences(Authentication authentication) {
        return userApplicationService.getCurrentUserPreferences(currentUser(authentication));
    }

    /**
     * Creates or updates preferences for the current authenticated user.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @param request Mutable preferences request body.
     * @param idempotencyKey Required idempotency header used to safely replay retries.
     * @param correlationId Optional correlation identifier header.
     * @param causationId Optional causation identifier header.
     * @param httpServletRequest Current HTTP request used for request-id fallback.
     * @return Returns the persisted preference response.
     */
    @PutMapping("/preferences")
    public UserPreferencesResponse updateCurrentUserPreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateUserPreferencesRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = currentUser(authentication);
        String resolvedCorrelationId = resolveCorrelationId(correlationId, httpServletRequest);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        return apiIdempotencyService.execute(
                authenticatedUser,
                UPDATE_PREFERENCES_OPERATION,
                idempotencyKey,
                request,
                UserPreferencesResponse.class,
                200,
                () -> userApplicationService.updateCurrentUserPreferences(authenticatedUser, request, resolvedCorrelationId, resolvedCausationId)
        );
    }

    /**
     * Lists addresses for the current authenticated user.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @return Returns a list of current user addresses.
     */
    @GetMapping("/addresses")
    public List<UserAddressResponse> getCurrentUserAddresses(Authentication authentication) {
        return userApplicationService.getCurrentUserAddresses(currentUser(authentication));
    }

    /**
     * Creates a new address for the current authenticated user.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @param request Mutable address request body.
     * @param idempotencyKey Required idempotency header used to safely replay retries.
     * @param correlationId Optional correlation identifier header.
     * @param causationId Optional causation identifier header.
     * @param httpServletRequest Current HTTP request used for request-id fallback.
     * @return Returns the persisted address response.
     */
    @PostMapping("/addresses")
    @ResponseStatus(CREATED)
    public UserAddressResponse createCurrentUserAddress(
            Authentication authentication,
            @Valid @RequestBody CreateUserAddressRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = currentUser(authentication);
        String resolvedCorrelationId = resolveCorrelationId(correlationId, httpServletRequest);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        return apiIdempotencyService.execute(
                authenticatedUser,
                CREATE_ADDRESS_OPERATION,
                idempotencyKey,
                request,
                UserAddressResponse.class,
                201,
                () -> userApplicationService.createCurrentUserAddress(authenticatedUser, request, resolvedCorrelationId, resolvedCausationId)
        );
    }

    /**
     * Extracts the tenant-scoped authenticated user context from Spring Security authentication.
     *
     * @param authentication Spring Security authentication containing the validated JWT.
     * @return Returns the extracted authenticated user context.
     */
    private AuthenticatedUser currentUser(Authentication authentication) {
        return jwtUserContextExtractor.extract(authentication);
    }

    /**
     * Resolves a correlation identifier using the provided header value or request-id fallback.
     *
     * @param correlationId Optional correlation identifier header value.
     * @param httpServletRequest Current HTTP request.
     * @return Returns a correlation identifier for event publication.
     */
    private String resolveCorrelationId(String correlationId, HttpServletRequest httpServletRequest) {
        return Optional.ofNullable(correlationId)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(httpServletRequest.getHeader("X-Request-Id"))
                        .filter(value -> !value.isBlank())
                        .orElseGet(() -> UUID.randomUUID().toString()));
    }

    /**
     * Resolves a causation identifier using the provided header value or the correlation identifier fallback.
     *
     * @param causationId Optional causation identifier header value.
     * @param correlationId Resolved correlation identifier.
     * @return Returns a causation identifier for event publication.
     */
    private String resolveCausationId(String causationId, String correlationId) {
        return Optional.ofNullable(causationId)
                .filter(value -> !value.isBlank())
                .orElse(correlationId);
    }
}

