package huynv.userservice.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

  /**
   * Verifies that the {@link UserEntity} canonical constructor stores the supplied identifiers and
   * automatically sets the initial status to {@link UserStatus#ACTIVE}.
   *
   * @return void — asserts identity fields and default status are set correctly after construction.
   */
  @Test
  void constructor_initializesWithActiveStatus() {
    UUID id = UUID.randomUUID();
    UUID keycloakId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    UserEntity user = new UserEntity(id, keycloakId, tenantId);

    assertThat(user.getId()).isEqualTo(id);
    assertThat(user.getKeycloakUserId()).isEqualTo(keycloakId);
    assertThat(user.getTenantId()).isEqualTo(tenantId);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  /**
   * Verifies that {@code applyProfile} writes all supplied mutable fields (email, full name, phone,
   * avatar URL, status, locale, timezone) to the entity.
   *
   * @return void — asserts that every mutable field is updated to the provided value.
   */
  @Test
  void applyProfile_updatesAllMutableFields() {
    UserEntity user = new UserEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    user.applyProfile(
        "alice@example.com",
        "Alice Smith",
        "+1234567890",
        "https://cdn.example.com/avatar.png",
        UserStatus.ACTIVE,
        "en-US",
        "America/New_York");

    assertThat(user.getEmail()).isEqualTo("alice@example.com");
    assertThat(user.getFullName()).isEqualTo("Alice Smith");
    assertThat(user.getPhoneNumber()).isEqualTo("+1234567890");
    assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getLocale()).isEqualTo("en-US");
    assertThat(user.getTimezone()).isEqualTo("America/New_York");
  }

  /**
   * Verifies that passing {@code null} as the status argument to {@code applyProfile} causes the entity
   * to retain {@link UserStatus#ACTIVE} rather than storing a null value.
   *
   * @return void — asserts that status defaults to {@code ACTIVE} when a null value is supplied.
   */
  @Test
  void applyProfile_nullStatus_defaultsToActive() {
    UserEntity user = new UserEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    user.applyProfile("a@b.com", null, null, null, null, null, null);

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  /**
   * Verifies that calling {@code applyProfile} does not alter the entity's immutable identity fields:
   * {@code id}, {@code keycloakUserId}, and {@code tenantId}.
   *
   * @return void — asserts that identity fields remain unchanged after a profile update.
   */
  @Test
  void applyProfile_doesNotChangeImmutableFields() {
    UUID id = UUID.randomUUID();
    UUID keycloakId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UserEntity user = new UserEntity(id, keycloakId, tenantId);

    user.applyProfile("x@y.com", "Name", null, null, UserStatus.ACTIVE, null, null);

    assertThat(user.getId()).isEqualTo(id);
    assertThat(user.getKeycloakUserId()).isEqualTo(keycloakId);
    assertThat(user.getTenantId()).isEqualTo(tenantId);
  }
}
