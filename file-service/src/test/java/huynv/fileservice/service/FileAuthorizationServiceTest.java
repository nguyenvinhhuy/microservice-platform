package huynv.fileservice.service;

import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileVisibility;
import huynv.fileservice.metrics.FileMetrics;
import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.security.JwtUserContextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAuthorizationServiceTest {

  @Mock private JwtUserContextExtractor jwtUserContextExtractor;
  @Mock private FileMetrics fileMetrics;

  private FileAuthorizationService service;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
  private static final UUID OWNER_USER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();

  /**
   * Initialises the {@link FileAuthorizationService} instance under test with mocked dependencies.
   *
   * @return void — sets up the service field before each test method.
   */
  @BeforeEach
  void setUp() {
    service = new FileAuthorizationService(jwtUserContextExtractor, fileMetrics);
  }

  // -----------------------------------------------------------------------
  // assertCanRead — PUBLIC visibility
  // -----------------------------------------------------------------------

  /**
   * Verifies that a PUBLIC file can be read without any authentication token present.
   *
   * @return void — asserts that no exception is thrown when authentication is null.
   */
  @Test
  void assertCanRead_publicFile_nullAuthentication_allowed() {
    FileRecord file = fileRecord(FileVisibility.PUBLIC, TENANT_ID, OWNER_USER_ID);

    assertThatCode(() -> service.assertCanRead(file, null)).doesNotThrowAnyException();
  }

  /**
   * Verifies that an authenticated user belonging to the same tenant can read a PUBLIC file.
   *
   * @return void — asserts that no exception is thrown for a same-tenant authenticated caller.
   */
  @Test
  void assertCanRead_publicFile_sameTenantAuthenticated_allowed() {
    FileRecord file = fileRecord(FileVisibility.PUBLIC, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(TENANT_ID, OTHER_USER_ID, false));

    assertThatCode(() -> service.assertCanRead(file, auth)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a non-privileged user from a different tenant is denied read access to a PUBLIC
   * file and that the access-denied metric is incremented.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown and the metric is recorded.
   */
  @Test
  void assertCanRead_publicFile_differentTenantNotPrivileged_denied() {
    FileRecord file = fileRecord(FileVisibility.PUBLIC, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(OTHER_TENANT_ID, OTHER_USER_ID, false));

    assertThatThrownBy(() -> service.assertCanRead(file, auth))
        .isInstanceOf(AccessDeniedException.class);
    verify(fileMetrics).recordAccessDenied();
  }

  /**
   * Verifies that a privileged user from a different tenant can read a PUBLIC file.
   *
   * @return void — asserts that no exception is thrown for a cross-tenant privileged caller.
   */
  @Test
  void assertCanRead_publicFile_differentTenantPrivileged_allowed() {
    FileRecord file = fileRecord(FileVisibility.PUBLIC, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(OTHER_TENANT_ID, OTHER_USER_ID, true));

    assertThatCode(() -> service.assertCanRead(file, auth)).doesNotThrowAnyException();
  }

  // -----------------------------------------------------------------------
  // assertCanRead — TENANT_SHARED visibility
  // -----------------------------------------------------------------------

  /**
   * Verifies that any authenticated user within the same tenant can read a TENANT_SHARED file.
   *
   * @return void — asserts that no exception is thrown for a non-owner, same-tenant user.
   */
  @Test
  void assertCanRead_tenantSharedFile_sameTenantAnyUser_allowed() {
    FileRecord file = fileRecord(FileVisibility.TENANT_SHARED, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(TENANT_ID, OTHER_USER_ID, false));

    assertThatCode(() -> service.assertCanRead(file, auth)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a user from a different tenant is denied read access to a TENANT_SHARED file.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown for a cross-tenant caller.
   */
  @Test
  void assertCanRead_tenantSharedFile_differentTenant_denied() {
    FileRecord file = fileRecord(FileVisibility.TENANT_SHARED, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(OTHER_TENANT_ID, OTHER_USER_ID, false));

    assertThatThrownBy(() -> service.assertCanRead(file, auth))
        .isInstanceOf(AccessDeniedException.class);
  }

  // -----------------------------------------------------------------------
  // assertCanRead — PRIVATE visibility
  // -----------------------------------------------------------------------

  /**
   * Verifies that the owning user can read their own PRIVATE file.
   *
   * @return void — asserts that no exception is thrown when the caller is the file owner.
   */
  @Test
  void assertCanRead_privateFile_ownerUser_allowed() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(TENANT_ID, OWNER_USER_ID, false));

    assertThatCode(() -> service.assertCanRead(file, auth)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a privileged (admin) user can read a PRIVATE file they do not own.
   *
   * @return void — asserts that no exception is thrown for a privileged non-owner.
   */
  @Test
  void assertCanRead_privateFile_privilegedUser_allowed() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(TENANT_ID, OTHER_USER_ID, true));

    assertThatCode(() -> service.assertCanRead(file, auth)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a non-privileged user who is not the file owner is denied read access to a
   * PRIVATE file within the same tenant.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown for a non-owner, non-privileged caller.
   */
  @Test
  void assertCanRead_privateFile_differentUserNotPrivileged_denied() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(TENANT_ID, OTHER_USER_ID, false));

    assertThatThrownBy(() -> service.assertCanRead(file, auth))
        .isInstanceOf(AccessDeniedException.class);
  }

  /**
   * Verifies that a user from a different tenant is denied read access to a PRIVATE file.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown for any cross-tenant caller.
   */
  @Test
  void assertCanRead_privateFile_differentTenant_denied() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    Authentication auth = mock(Authentication.class);
    when(jwtUserContextExtractor.extract(auth)).thenReturn(user(OTHER_TENANT_ID, OTHER_USER_ID, false));

    assertThatThrownBy(() -> service.assertCanRead(file, auth))
        .isInstanceOf(AccessDeniedException.class);
  }

  // -----------------------------------------------------------------------
  // assertCanMutate
  // -----------------------------------------------------------------------

  /**
   * Verifies that the file owner is allowed to mutate their own file.
   *
   * @return void — asserts that no exception is thrown when the caller is the file owner.
   */
  @Test
  void assertCanMutate_ownerUser_allowed() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    AuthenticatedUser user = user(TENANT_ID, OWNER_USER_ID, false);

    assertThatCode(() -> service.assertCanMutate(file, user)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a privileged (admin) user can mutate a file they do not own.
   *
   * @return void — asserts that no exception is thrown for a privileged non-owner caller.
   */
  @Test
  void assertCanMutate_privilegedUser_allowed() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    AuthenticatedUser user = user(TENANT_ID, OTHER_USER_ID, true);

    assertThatCode(() -> service.assertCanMutate(file, user)).doesNotThrowAnyException();
  }

  /**
   * Verifies that a user from a different tenant is denied mutation access and that the
   * access-denied metric is recorded.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown and the metric is incremented.
   */
  @Test
  void assertCanMutate_differentTenant_denied() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    AuthenticatedUser user = user(OTHER_TENANT_ID, OWNER_USER_ID, false);

    assertThatThrownBy(() -> service.assertCanMutate(file, user))
        .isInstanceOf(AccessDeniedException.class);
    verify(fileMetrics).recordAccessDenied();
  }

  /**
   * Verifies that a non-privileged user within the same tenant who is not the file owner is denied
   * mutation access.
   *
   * @return void — asserts that {@link AccessDeniedException} is thrown for a non-owner, non-privileged caller.
   */
  @Test
  void assertCanMutate_differentUserNotPrivileged_denied() {
    FileRecord file = fileRecord(FileVisibility.PRIVATE, TENANT_ID, OWNER_USER_ID);
    AuthenticatedUser user = user(TENANT_ID, OTHER_USER_ID, false);

    assertThatThrownBy(() -> service.assertCanMutate(file, user))
        .isInstanceOf(AccessDeniedException.class);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Creates a mocked {@link FileRecord} with the given visibility, tenant, and owner identifiers.
   *
   * @param visibility the file visibility level to stub.
   * @param tenantId the tenant that owns the file.
   * @param ownerUserId the user ID of the file's owner.
   * @return a lenient Mockito mock configured with the provided attribute values.
   */
  private FileRecord fileRecord(FileVisibility visibility, UUID tenantId, UUID ownerUserId) {
    FileRecord f = mock(FileRecord.class);
    lenient().when(f.getVisibility()).thenReturn(visibility);
    lenient().when(f.getTenantId()).thenReturn(tenantId);
    lenient().when(f.getOwnerUserId()).thenReturn(ownerUserId);
    return f;
  }

  /**
   * Builds an {@link AuthenticatedUser} with the given tenant, user identity, and privilege level.
   *
   * @param tenantId the tenant the user belongs to.
   * @param userId the unique identifier of the user.
   * @param privileged {@code true} to assign {@code ROLE_ADMIN}; {@code false} for {@code ROLE_USER}.
   * @return a fully constructed {@link AuthenticatedUser} instance.
   */
  private AuthenticatedUser user(UUID tenantId, UUID userId, boolean privileged) {
    Set<String> roles = privileged ? Set.of("ROLE_ADMIN") : Set.of("ROLE_USER");
    return new AuthenticatedUser(userId, tenantId, roles);
  }
}
