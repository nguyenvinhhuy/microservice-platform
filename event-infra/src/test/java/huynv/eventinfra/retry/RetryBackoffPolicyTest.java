package huynv.eventinfra.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryBackoffPolicyTest {

  /**
   * Verifies that the first retry attempt maps to a backoff delay of 1 minute,
   * matching the first tier of the standard retry schedule.
   *
   * @return void — asserts that attempt 1 produces a delay of exactly 1 minute.
   */
  @Test
  void nextDelay_attempt1_returns1Minute() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMinutes(1));
  }

  /**
   * Verifies that the second retry attempt maps to a backoff delay of 5 minutes,
   * matching the second tier of the standard retry schedule.
   *
   * @return void — asserts that attempt 2 produces a delay of exactly 5 minutes.
   */
  @Test
  void nextDelay_attempt2_returns5Minutes() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofMinutes(5));
  }

  /**
   * Verifies that the third retry attempt maps to a backoff delay of 30 minutes,
   * matching the third and final tier of the standard retry schedule.
   *
   * @return void — asserts that attempt 3 produces a delay of exactly 30 minutes.
   */
  @Test
  void nextDelay_attempt3_returns30Minutes() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMinutes(30));
  }

  /**
   * Verifies that an attempt number exceeding the configured maximum returns {@code null},
   * signalling that the message should be routed to the DLQ instead of retried again.
   *
   * @return void — asserts that attempt 4 on a max-3 policy produces a null delay.
   */
  @Test
  void nextDelay_attemptBeyondMax_returnsNull() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(4)).isNull();
  }

  /**
   * Verifies that attempt zero returns {@code null}, confirming that only positive attempt
   * numbers are treated as valid retry indices by the backoff policy.
   *
   * @return void — asserts that attempt 0 produces a null delay.
   */
  @Test
  void nextDelay_attemptZero_returnsNull() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(0)).isNull();
  }

  /**
   * Verifies that a negative attempt number returns {@code null}, confirming that the backoff
   * policy rejects out-of-range inputs gracefully rather than throwing an exception.
   *
   * @return void — asserts that a negative attempt number produces a null delay.
   */
  @Test
  void nextDelay_negativeAttempt_returnsNull() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.nextDelay(-1)).isNull();
  }

  /**
   * Verifies that attempt numbers between 3 and {@code maxAttempts} are capped at the
   * 30-minute tier, so that later retries within a generous limit share the longest backoff.
   *
   * @return void — asserts that attempts 4 and 5 on a max-5 policy both return 30 minutes.
   */
  @Test
  void nextDelay_attemptBeyond3ButWithinMax_returns30Minutes() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(5);
    assertThat(policy.nextDelay(4)).isEqualTo(Duration.ofMinutes(30));
    assertThat(policy.nextDelay(5)).isEqualTo(Duration.ofMinutes(30));
  }

  /**
   * Verifies that constructing a {@link RetryBackoffPolicy} with a negative maximum-attempts
   * value throws an {@link IllegalArgumentException}, enforcing a valid configuration contract.
   *
   * @return void — asserts that an IllegalArgumentException is raised for a negative max-attempts value.
   */
  @Test
  void constructor_negativeMaxAttempts_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new RetryBackoffPolicy(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Verifies that {@link RetryBackoffPolicy#maxAttempts()} returns the value supplied at
   * construction time, confirming the accessor reflects the configured policy limit.
   *
   * @return void — asserts that maxAttempts returns 7 when the policy was constructed with 7.
   */
  @Test
  void maxAttempts_returnsConfiguredValue() {
    assertThat(new RetryBackoffPolicy(7).maxAttempts()).isEqualTo(7);
  }

  /**
   * Verifies that {@link RetryBackoffPolicy#normalizeAttempt} treats a {@code null} input
   * as attempt 1, preventing NPEs when the attempt count header is absent from a Kafka record.
   *
   * @return void — asserts that a null attempt is normalised to 1.
   */
  @Test
  void normalizeAttempt_nullInput_returns1() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.normalizeAttempt(null)).isEqualTo(1);
  }

  /**
   * Verifies that {@link RetryBackoffPolicy#normalizeAttempt} clamps values that exceed
   * {@code maxAttempts} to the configured maximum, preventing out-of-bounds lookups.
   *
   * @return void — asserts that an over-large attempt count is clamped to maxAttempts.
   */
  @Test
  void normalizeAttempt_exceedsMax_clampsToMax() {
    RetryBackoffPolicy policy = new RetryBackoffPolicy(3);
    assertThat(policy.normalizeAttempt(99)).isEqualTo(3);
  }
}
