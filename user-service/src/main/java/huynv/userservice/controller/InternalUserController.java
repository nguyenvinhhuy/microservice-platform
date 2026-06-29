package huynv.userservice.controller;

import huynv.userservice.dto.InternalUserContactResponse;
import huynv.userservice.security.JwtUserContextExtractor;
import huynv.userservice.service.UserApplicationService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes trusted internal user lookup APIs for platform service-to-service integrations.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserApplicationService userApplicationService;
    private final JwtUserContextExtractor jwtUserContextExtractor;

    /**
     * Creates an internal user API controller.
     *
     * @param userApplicationService Application service implementing internal lookup use cases.
     * @param jwtUserContextExtractor JWT extractor used to resolve tenant-scoped caller identity.
     * @return Initializes an internal user API controller instance.
     */
    public InternalUserController(
        UserApplicationService userApplicationService,
        JwtUserContextExtractor jwtUserContextExtractor
    ) {
        this.userApplicationService = Objects.requireNonNull(userApplicationService, "userApplicationService");
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
    }

    /**
     * Returns trusted contact information for a tenant-scoped user.
     *
     * @param jwt Spring Security authentication containing the validated JWT.
     * @param userId Domain user identifier to resolve.
     * @return Returns the requested internal contact response.
     */
    @GetMapping("/{userId}/contact")
    public InternalUserContactResponse getInternalUserContact(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID userId
    ) {
        return userApplicationService.getInternalUserContact(jwtUserContextExtractor.extractTenantId(jwt), userId);
    }
}
