package huynv.gatewayservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRolesGrantedAuthoritiesConverterTest {

  private KeycloakRolesGrantedAuthoritiesConverter converter;

  /**
   * Creates a fresh {@link KeycloakRolesGrantedAuthoritiesConverter} instance before each test
   * to ensure no shared mutable state between test executions.
   *
   * @return void — sets up the converter field before each test method.
   */
  @BeforeEach
  void setUp() {
    converter = new KeycloakRolesGrantedAuthoritiesConverter();
  }

  /**
   * Verifies that roles present in the JWT {@code realm_access.roles} claim are converted to
   * Spring {@link GrantedAuthority} values with the {@code ROLE_} prefix and uppercased role names.
   *
   * @return void — asserts that the returned authorities match the expected {@code ROLE_ADMIN} and {@code ROLE_USER} values.
   */
  @Test
  void convert_withRealmRoles_mapsToRolePrefixedAuthorities() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "user"))));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
  }

  /**
   * Verifies that when the JWT contains no {@code realm_access} claim, the converter returns an
   * empty collection rather than throwing an exception.
   *
   * @return void — asserts that the returned authority collection is empty.
   */
  @Test
  void convert_noRealmAccessClaim_returnsEmptyCollection() {
    Jwt jwt = jwt(Map.of());

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities).isEmpty();
  }

  /**
   * Verifies that when the JWT contains a {@code realm_access.roles} claim with an empty list,
   * the converter returns an empty collection of authorities.
   *
   * @return void — asserts that the returned authority collection is empty when no roles are present.
   */
  @Test
  void convert_emptyRolesList_returnsEmptyCollection() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of())));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities).isEmpty();
  }

  /**
   * Verifies that null entries within the {@code realm_access.roles} list are silently skipped,
   * and only the valid non-null roles are mapped to authorities.
   *
   * @return void — asserts that the returned authorities contain only {@code ROLE_VIEWER} and not a null-derived entry.
   */
  @Test
  void convert_roleIsNull_nullEntrySkipped() {
    List<Object> rolesWithNull = new ArrayList<>();
    rolesWithNull.add(null);
    rolesWithNull.add("viewer");
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", rolesWithNull)));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_VIEWER");
  }

  /**
   * Verifies that blank (whitespace-only) role strings within the {@code realm_access.roles} list
   * are silently skipped, and only the valid non-blank roles are mapped to authorities.
   *
   * @return void — asserts that the returned authorities contain only {@code ROLE_MANAGER} and not a blank-derived entry.
   */
  @Test
  void convert_roleIsBlank_blankEntrySkipped() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("  ", "manager"))));

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_MANAGER");
  }

  /**
   * Builds a minimal {@link Jwt} instance populated with standard token metadata and the provided
   * extra claims, for use as test input to the converter.
   *
   * @param extraClaims additional JWT claims to merge into the token, such as {@code realm_access}.
   * @return a fully constructed {@link Jwt} with the standard test token value, subject, issuer, and extra claims.
   */
  private static Jwt jwt(Map<String, Object> extraClaims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("user-subject")
            .issuer("http://keycloak:8180/realms/test")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600));
    extraClaims.forEach(builder::claim);
    return builder.build();
  }
}
