package huynv.userservice.security;

import huynv.userservice.config.UserServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the user-service internal machine-to-machine authorization rules.
 */
class InternalAccessEvaluatorTest {

    /**
     * Accepts a machine token only when it carries an allowed azp and one of the trusted audience, scope, or service-role claims.
     *
     * @return Verifies that the tightened internal trust model still allows valid machine credentials.
     */
    @Test
    void allowsTrustedMachineToken() {
        InternalAccessEvaluator evaluator = new InternalAccessEvaluator(properties());
        Jwt jwt = jwt(Map.of(
                "azp", "gateway-service",
                "aud", List.of("user-service-internal"),
                "scope", "user-service.internal"
        ));

        assertThat(evaluator.hasInternalAccess(jwt)).isTrue();
    }

    /**
     * Rejects a machine token when the authorized party claim is absent even if the audience or scopes look correct.
     *
     * @return Verifies that internal endpoints require a trusted calling service identity.
     */
    @Test
    void rejectsTokenWithoutAuthorizedParty() {
        InternalAccessEvaluator evaluator = new InternalAccessEvaluator(properties());
        Jwt jwt = jwt(Map.of(
                "aud", List.of("user-service-internal"),
                "scope", "user-service.internal"
        ));

        assertThat(evaluator.hasInternalAccess(jwt)).isFalse();
    }

    /**
     * Rejects a token when the caller azp is not part of the configured trusted service allow-list.
     *
     * @return Verifies that arbitrary end-user clients cannot reuse internal scopes against internal APIs.
     */
    @Test
    void rejectsUnknownAuthorizedParty() {
        InternalAccessEvaluator evaluator = new InternalAccessEvaluator(properties());
        Jwt jwt = jwt(Map.of(
                "azp", "web-frontend",
                "aud", List.of("user-service-internal"),
                "scope", "user-service.internal"
        ));

        assertThat(evaluator.hasInternalAccess(jwt)).isFalse();
    }

    /**
     * Creates the minimal service properties required for internal access evaluation.
     *
     * @return Returns user-service properties with deterministic internal allow-lists.
     */
    private UserServiceProperties properties() {
        UserServiceProperties properties = new UserServiceProperties();
        properties.getSecurity().getInternal().setAllowedAuthorizedParties(List.of("gateway-service", "notification-service"));
        properties.getSecurity().getInternal().setAllowedAudiences(List.of("user-service-internal"));
        properties.getSecurity().getInternal().setAllowedScopes(List.of("user-service.internal"));
        properties.getSecurity().getInternal().setAllowedServiceRoles(List.of("service_user_reader"));
        return properties;
    }

    /**
     * Creates a deterministic JWT instance for unit tests.
     *
     * @param claims Claims to include in the JWT body.
     * @return Returns a JWT backed by the provided claims.
     */
    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.parse("2026-05-01T12:00:00Z"),
                Instant.parse("2026-05-01T13:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
    }
}

