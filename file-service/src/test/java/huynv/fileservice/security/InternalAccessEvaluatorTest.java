package huynv.fileservice.security;

import huynv.fileservice.config.FileServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the trusted internal JWT authorization rules.
 */
class InternalAccessEvaluatorTest {

    /**
     * Verifies that a JWT with the configured azp, audience, and scope is allowed.
     *
     * @return Performs assertions against the evaluator result.
     */
    @Test
    void hasInternalAccessReturnsTrueForConfiguredTrustedCaller() {
        FileServiceProperties properties = new FileServiceProperties();
        InternalAccessEvaluator evaluator = new InternalAccessEvaluator(properties);
        Jwt jwt = jwt(Map.of(
                "azp", "gateway-service",
                "aud", List.of("file-service-internal"),
                "scope", "file-service.internal"
        ));

        assertThat(evaluator.hasInternalAccess(jwt)).isTrue();
    }

    /**
     * Verifies that the azp claim is mandatory even when audience and scope match.
     *
     * @return Performs assertions against the evaluator result.
     */
    @Test
    void hasInternalAccessReturnsFalseWithoutAuthorizedParty() {
        FileServiceProperties properties = new FileServiceProperties();
        InternalAccessEvaluator evaluator = new InternalAccessEvaluator(properties);
        Jwt jwt = jwt(Map.of(
                "aud", List.of("file-service-internal"),
                "scope", "file-service.internal"
        ));

        assertThat(evaluator.hasInternalAccess(jwt)).isFalse();
    }

    /**
     * Creates a JWT suitable for evaluator tests.
     *
     * @param claims JWT claims to embed.
     * @return Returns a test JWT instance.
     */
    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "none"), claims);
    }
}

