package huynv.fileservice.security;

import huynv.fileservice.config.FileServiceProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates whether a validated JWT may access trusted internal-service endpoints.
 */
@Component
public class InternalAccessEvaluator {

    private final FileServiceProperties fileServiceProperties;

    /**
     * Creates an evaluator using the configured machine-to-machine authorization rules.
     *
     * @param fileServiceProperties File-service properties containing trusted JWT claim values.
     * @return Initializes the internal access evaluator.
     */
    public InternalAccessEvaluator(FileServiceProperties fileServiceProperties) {
        this.fileServiceProperties = Objects.requireNonNull(fileServiceProperties, "fileServiceProperties");
    }

    /**
     * Determines whether the supplied JWT may access trusted internal endpoints.
     *
     * @param jwt Validated JWT token.
     * @return Returns true when the token satisfies the configured internal access rules.
     */
    public boolean hasInternalAccess(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        FileServiceProperties.Security.Internal internal = fileServiceProperties.getSecurity().getInternal();
        String authorizedParty = jwt.getClaimAsString("azp");
        boolean allowedParty = authorizedParty != null && internal.getAllowedAuthorizedParties().contains(authorizedParty);
        boolean allowedAudience = asStrings(jwt.getClaim("aud")).stream().anyMatch(internal.getAllowedAudiences()::contains);
        boolean allowedScope = scopes(jwt).stream().anyMatch(internal.getAllowedScopes()::contains);
        boolean allowedServiceRole = realmRoles(jwt).stream().anyMatch(internal.getAllowedServiceRoles()::contains);
        return allowedParty && (allowedAudience || allowedScope || allowedServiceRole);
    }

    /**
     * Converts a JWT claim value into a set of strings.
     *
     * @param claim Raw JWT claim value.
     * @return Returns the claim values converted into a string set.
     */
    private Set<String> asStrings(Object claim) {
        if (claim instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        if (claim == null) {
            return Collections.emptySet();
        }
        return Set.of(String.valueOf(claim));
    }

    /**
     * Extracts OAuth scopes from the JWT into a normalized string set.
     *
     * @param jwt Validated JWT token.
     * @return Returns the token scopes as a string set.
     */
    private Set<String> scopes(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope == null) {
            return Collections.emptySet();
        }
        return Set.of(String.valueOf(scope).split(" "));
    }

    /**
     * Extracts Keycloak realm roles from the JWT into a normalized role set.
     *
     * @param jwt Validated JWT token.
     * @return Returns the token realm roles as a string set.
     */
    @SuppressWarnings("unchecked")
    private Set<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return Collections.emptySet();
        }
        Object roles = realmAccessMap.get("roles");
        if (!(roles instanceof Collection<?> collection)) {
            return Collections.emptySet();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .collect(java.util.stream.Collectors.toSet());
    }
}

