package huynv.userservice.security;

import huynv.userservice.config.UserServiceProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates whether a validated JWT represents a trusted internal service caller.
 */
@Component
public class InternalAccessEvaluator {

    private final UserServiceProperties userServiceProperties;

    /**
     * Creates an internal access evaluator backed by validated service security properties.
     *
     * @param userServiceProperties User-service security properties containing trusted audiences, scopes, and clients.
     * @return Initializes an internal access evaluator instance.
     */
    public InternalAccessEvaluator(UserServiceProperties userServiceProperties) {
        this.userServiceProperties = Objects.requireNonNull(userServiceProperties, "userServiceProperties");
    }

    /**
     * Determines whether the given JWT is authorized for trusted internal user-service endpoints.
     *
     * @param jwt Validated JWT token.
     * @return Returns true when the token satisfies the configured internal service trust policy.
     */
    public boolean hasInternalAccess(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        UserServiceProperties.Internal internal = userServiceProperties.getSecurity().getInternal();
        String authorizedParty = jwt.getClaimAsString("azp");
        boolean allowedParty = authorizedParty != null && internal.getAllowedAuthorizedParties().contains(authorizedParty);
        boolean allowedAudience = asStrings(jwt.getClaim("aud")).stream().anyMatch(internal.getAllowedAudiences()::contains);
        boolean allowedScope = scopes(jwt).stream().anyMatch(internal.getAllowedScopes()::contains);
        boolean allowedServiceRole = realmRoles(jwt).stream().anyMatch(internal.getAllowedServiceRoles()::contains);
        return allowedParty && (allowedAudience || allowedScope || allowedServiceRole);
    }

    /**
     * Extracts OAuth scopes from either the {@code scope} or {@code scp} claims.
     *
     * @param jwt Validated JWT token.
     * @return Returns normalized scope values.
     */
    private Set<String> scopes(Jwt jwt) {
        List<String> scopes = new ArrayList<>();
        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim instanceof String scopeString) {
            for (String scope : scopeString.split("\\s+")) {
                if (!scope.isBlank()) {
                    scopes.add(scope.trim());
                }
            }
        }
        scopes.addAll(asStrings(jwt.getClaim("scp")));
        return scopes.stream().map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    /**
     * Extracts Keycloak realm roles from the validated token.
     *
     * @param jwt Validated JWT token.
     * @return Returns normalized realm role values.
     */
    private Set<String> realmRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Set.of();
        }
        Object roles = realmAccess.get("roles");
        return asStrings(roles).stream().map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    /**
     * Converts a claim value into a string collection.
     *
     * @param value Claim value that may be a string, collection, or null.
     * @return Returns a normalized collection of string values.
     */
    private Collection<String> asStrings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String stringValue) {
            return List.of(stringValue);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}


