package huynv.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.userservice.config.UserServiceProperties;
import huynv.userservice.domain.ApiIdempotencyEntity;
import huynv.userservice.domain.ApiIdempotencyState;
import huynv.userservice.exception.BadRequestException;
import huynv.userservice.exception.ConflictException;
import huynv.userservice.metrics.UserMetrics;
import huynv.userservice.repository.ApiIdempotencyRepository;
import huynv.userservice.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiIdempotencyServiceTest {

  @Mock private ApiIdempotencyRepository apiIdempotencyRepository;
  @Mock private UserMetrics userMetrics;
  @Mock private PlatformTransactionManager txManager;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ApiIdempotencyService service;
  private AuthenticatedUser authUser;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OP = "UPDATE_PROFILE";
  private static final String KEY = "idem-key-1";

  /**
   * Builds lenient mocks for {@link PlatformTransactionManager}, {@link UserServiceProperties}, and
   * {@link AuthenticatedUser}, then constructs the {@link ApiIdempotencyService} under test so that
   * all transaction-template callbacks execute inline without a real database transaction.
   *
   * @return void — initializes shared fixtures required by every test method in this class.
   */
  @BeforeEach
  void setUp() {
    TransactionStatus txStatus = mock(TransactionStatus.class);
    // Make TransactionTemplate.execute() run callbacks inline without a real transaction.
    lenient().when(txManager.getTransaction(any())).thenReturn(txStatus);

    UserServiceProperties.Idempotency idempotencyProps =
        mock(UserServiceProperties.Idempotency.class, withLenientStrictness());
    lenient().when(idempotencyProps.getTtl()).thenReturn(Duration.ofMinutes(10));
    lenient().when(idempotencyProps.isEnabled()).thenReturn(true);

    UserServiceProperties props = mock(UserServiceProperties.class, withLenientStrictness());
    lenient().when(props.getIdempotency()).thenReturn(idempotencyProps);

    authUser = mock(AuthenticatedUser.class);
    // Lenient: validation tests throw before authUser fields are accessed.
    lenient().when(authUser.tenantId()).thenReturn(TENANT_ID);
    lenient().when(authUser.userId()).thenReturn(USER_ID);

    service =
        new ApiIdempotencyService(apiIdempotencyRepository, objectMapper, props, userMetrics, txManager);
  }

  // -----------------------------------------------------------------------
  // execute — new key, action succeeds
  // -----------------------------------------------------------------------

  /**
   * Verifies that a brand-new idempotency key causes the service to claim a new row, execute the
   * supplied action, persist the result, and return the action's return value to the caller.
   *
   * @return void — asserts that the action result is returned and both the claim and completion saves are performed.
   */
  @Test
  void execute_newKey_actionSucceeds_persistsAndReturnsResult() {
    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.empty());
    when(apiIdempotencyRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(apiIdempotencyRepository.findById(any()))
        .thenAnswer(
            inv ->
                Optional.of(
                    entity(inv.getArgument(0), hashOf(OP, null), ApiIdempotencyState.PROCESSING)));
    when(apiIdempotencyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    String result = service.execute(authUser, OP, KEY, null, String.class, 200, () -> "created");

    assertThat(result).isEqualTo("created");
    verify(apiIdempotencyRepository).saveAndFlush(any()); // new row claimed
    verify(apiIdempotencyRepository).save(any()); // markCompleted
  }

  // -----------------------------------------------------------------------
  // execute — cached response replay
  // -----------------------------------------------------------------------

  /**
   * Verifies that when a completed idempotency row already exists for the key, the service deserializes
   * and returns the cached response body without executing the action or creating a new row.
   *
   * @return void — asserts that the cached response is returned and no new idempotency row is saved.
   * @throws Exception if JSON serialization fails during test setup.
   */
  @Test
  void execute_existingCompletedRow_returnsCachedResponseWithoutRunningAction() throws Exception {
    ApiIdempotencyEntity completed = entity(UUID.randomUUID(), hashOf(OP, null), ApiIdempotencyState.COMPLETED);
    completed.markCompleted(200, "\"cached-result\"");

    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.of(completed));

    String result = service.execute(authUser, OP, KEY, null, String.class, 200, () -> "fresh");

    assertThat(result).isEqualTo("cached-result");
    verify(apiIdempotencyRepository, never()).saveAndFlush(any());
  }

  // -----------------------------------------------------------------------
  // execute — in-flight conflict
  // -----------------------------------------------------------------------

  /**
   * Verifies that when an idempotency row is already in the {@code PROCESSING} state (i.e., a concurrent
   * request is in flight), the service throws a {@link ConflictException} immediately without starting
   * another execution.
   *
   * @return void — asserts that {@code ConflictException} is thrown and no new row is saved.
   */
  @Test
  void execute_existingProcessingRow_throwsConflictException() {
    ApiIdempotencyEntity processing = entity(UUID.randomUUID(), hashOf(OP, null), ApiIdempotencyState.PROCESSING);

    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.of(processing));

    assertThatThrownBy(() -> service.execute(authUser, OP, KEY, null, String.class, 200, () -> "x"))
        .isInstanceOf(ConflictException.class);

    verify(apiIdempotencyRepository, never()).saveAndFlush(any());
  }

  // -----------------------------------------------------------------------
  // execute — request hash mismatch
  // -----------------------------------------------------------------------

  /**
   * Verifies that when the request body hash of a new call does not match the hash stored in an existing
   * completed idempotency row, the service throws a {@link ConflictException} to signal the
   * key is being reused with a different payload.
   *
   * @return void — asserts that {@code ConflictException} is thrown on a request-hash mismatch.
   */
  @Test
  void execute_differentRequestBody_throwsConflictException() {
    // Entity was saved with hash of a non-null body — will mismatch the null-body hash below.
    String differentHash = hashOf(OP, "other-payload");
    ApiIdempotencyEntity existing = entity(UUID.randomUUID(), differentHash, ApiIdempotencyState.COMPLETED);

    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.of(existing));

    // Caller sends null body — hash mismatch
    assertThatThrownBy(() -> service.execute(authUser, OP, KEY, null, String.class, 200, () -> "x"))
        .isInstanceOf(ConflictException.class);
  }

  // -----------------------------------------------------------------------
  // execute — failed row is restarted
  // -----------------------------------------------------------------------

  /**
   * Verifies that when an existing idempotency row is in the {@code FAILED} state, the service restarts
   * it by saving the updated row, re-executes the action, and returns the new result.
   *
   * @return void — asserts that the action is re-run and the restarted row is saved via {@code saveAndFlush}.
   */
  @Test
  void execute_existingFailedRow_restartsRowAndRunsAction() {
    ApiIdempotencyEntity failed = entity(UUID.randomUUID(), hashOf(OP, null), ApiIdempotencyState.FAILED);

    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.of(failed));
    when(apiIdempotencyRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(apiIdempotencyRepository.findById(any()))
        .thenAnswer(inv -> Optional.of(entity(inv.getArgument(0), hashOf(OP, null), ApiIdempotencyState.PROCESSING)));
    when(apiIdempotencyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    String result = service.execute(authUser, OP, KEY, null, String.class, 200, () -> "retried");

    assertThat(result).isEqualTo("retried");
    // restart() calls saveAndFlush on the existing entity
    verify(apiIdempotencyRepository).saveAndFlush(any());
  }

  // -----------------------------------------------------------------------
  // execute — action throws → row marked FAILED, exception rethrown
  // -----------------------------------------------------------------------

  /**
   * Verifies that when the supplied action throws an exception, the service transitions the idempotency
   * row to {@code FAILED} state and rethrows the original exception so the caller receives the error.
   *
   * @return void — asserts that the row state is {@code FAILED} and the original exception is propagated.
   */
  @Test
  void execute_actionThrows_marksRowFailedAndRethrowsException() {
    when(apiIdempotencyRepository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            TENANT_ID, USER_ID, OP, KEY))
        .thenReturn(Optional.empty());
    when(apiIdempotencyRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    ApiIdempotencyEntity processingEntity =
        entity(UUID.randomUUID(), hashOf(OP, null), ApiIdempotencyState.PROCESSING);
    when(apiIdempotencyRepository.findById(any())).thenReturn(Optional.of(processingEntity));
    when(apiIdempotencyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    assertThatThrownBy(
            () ->
                service.execute(
                    authUser,
                    OP,
                    KEY,
                    null,
                    String.class,
                    200,
                    () -> {
                      throw new RuntimeException("downstream failure");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("downstream failure");

    // The entity fetched in markFailed should be saved with FAILED state
    verify(apiIdempotencyRepository).save(any());
    assertThat(processingEntity.getState()).isEqualTo(ApiIdempotencyState.FAILED);
  }

  // -----------------------------------------------------------------------
  // execute — validation
  // -----------------------------------------------------------------------

  /**
   * Verifies that passing a blank (whitespace-only) idempotency key is rejected immediately with a
   * {@link BadRequestException} before any repository interaction occurs.
   *
   * @return void — asserts that {@code BadRequestException} is thrown for a blank idempotency key.
   */
  @Test
  void execute_blankIdempotencyKey_throwsBadRequestException() {
    assertThatThrownBy(
            () -> service.execute(authUser, OP, "  ", null, String.class, 200, () -> "x"))
        .isInstanceOf(BadRequestException.class);
  }

  /**
   * Verifies that passing a null operation name is rejected immediately with a {@link BadRequestException}
   * before any repository interaction occurs.
   *
   * @return void — asserts that {@code BadRequestException} is thrown when the operation parameter is null.
   */
  @Test
  void execute_nullOperation_throwsBadRequestException() {
    assertThatThrownBy(
            () -> service.execute(authUser, null, KEY, null, String.class, 200, () -> "x"))
        .isInstanceOf(BadRequestException.class);
  }

  // -----------------------------------------------------------------------
  // cleanupExpired — feature flag
  // -----------------------------------------------------------------------

  /**
   * Verifies that when the idempotency feature flag is disabled, {@code cleanupExpired} returns
   * immediately without invoking the repository's {@code deleteExpired} method.
   *
   * @return void — asserts that {@code deleteExpired} is never called when the feature is disabled.
   */
  @Test
  void cleanupExpired_whenDisabled_skipsDeleteExpired() {
    // Override isEnabled to return false for this test only.
    UserServiceProperties.Idempotency disabledProps =
        mock(UserServiceProperties.Idempotency.class, withLenientStrictness());
    lenient().when(disabledProps.isEnabled()).thenReturn(false);
    lenient().when(disabledProps.getTtl()).thenReturn(Duration.ofMinutes(10));

    UserServiceProperties disabledServiceProps = mock(UserServiceProperties.class, withLenientStrictness());
    lenient().when(disabledServiceProps.getIdempotency()).thenReturn(disabledProps);

    ApiIdempotencyService disabledService =
        new ApiIdempotencyService(
            apiIdempotencyRepository, objectMapper, disabledServiceProps, userMetrics, txManager);

    disabledService.cleanupExpired();

    verify(apiIdempotencyRepository, never()).deleteExpired(any());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Constructs an {@link ApiIdempotencyEntity} with the given identifier, request hash, and desired state,
   * applying the appropriate state-transition method ({@code markFailed} or {@code markCompleted}) so
   * tests receive an entity that accurately reflects the target lifecycle stage.
   *
   * @param id the UUID to assign as the entity's primary key.
   * @param requestHash the SHA-256 hash string representing the original request body.
   * @param state the desired {@link ApiIdempotencyState} for the returned entity.
   * @return an {@link ApiIdempotencyEntity} in the requested state, scoped to the shared tenant and user IDs.
   */
  private ApiIdempotencyEntity entity(UUID id, String requestHash, ApiIdempotencyState state) {
    ApiIdempotencyEntity e =
        new ApiIdempotencyEntity(
            id,
            TENANT_ID,
            USER_ID,
            OP,
            KEY,
            requestHash,
            Instant.now().plusSeconds(600));
    if (state == ApiIdempotencyState.FAILED) {
      e.markFailed();
    } else if (state == ApiIdempotencyState.COMPLETED) {
      e.markCompleted(200, "\"ok\"");
    }
    return e;
  }

  /**
   * Computes the SHA-256 Base64url hash over the concatenation of the operation name and the JSON
   * serialization of the body (if non-null), replicating the hash algorithm used internally by
   * {@link ApiIdempotencyService} so that test stubs can supply matching hashes.
   *
   * @param operation the operation name to include in the hash input.
   * @param body the optional request body object; its JSON representation is appended when non-null.
   * @return the Base64url-encoded (no padding) SHA-256 hash string.
   */
  private String hashOf(String operation, Object body) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(operation.getBytes(StandardCharsets.UTF_8));
      if (body != null) {
        md.update(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
      }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a {@link org.mockito.MockSettings} instance configured with
   * {@link org.mockito.quality.Strictness#LENIENT} so that mocks created with these settings do not fail
   * the test if their stubbed interactions are never invoked.
   *
   * @return lenient {@link org.mockito.MockSettings} for use with {@code Mockito.mock(Class, MockSettings)}.
   */
  private static org.mockito.MockSettings withLenientStrictness() {
    return org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT);
  }
}
