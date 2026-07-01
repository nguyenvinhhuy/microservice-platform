package huynv.event.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyServiceTest {

  private InMemoryIdempotencyService service;

  /**
   * Creates a fresh {@link InMemoryIdempotencyService} instance before each test
   * so that tests do not share mutable state.
   *
   * @return void — initialises the {@code service} field used by each test method.
   */
  @BeforeEach
  void setUp() {
    service = new InMemoryIdempotencyService();
  }

  /**
   * Verifies that querying an event ID that has never been marked returns {@code false},
   * confirming that the service starts with an empty processed-event registry.
   *
   * @return void — asserts that {@code alreadyProcessed} returns false for an unseen event ID.
   */
  @Test
  void alreadyProcessed_newEventId_returnsFalse() {
    assertThat(service.alreadyProcessed("evt-001")).isFalse();
  }

  /**
   * Verifies that after calling {@link InMemoryIdempotencyService#markProcessed}, a subsequent
   * call to {@link InMemoryIdempotencyService#alreadyProcessed} for the same event ID returns
   * {@code true}.
   *
   * @return void — asserts that the event is recognised as processed after being marked.
   */
  @Test
  void markProcessed_thenAlreadyProcessed_returnsTrue() {
    service.markProcessed("evt-002");
    assertThat(service.alreadyProcessed("evt-002")).isTrue();
  }

  /**
   * Verifies that marking one event as processed does not affect the processed state of a
   * different event ID, confirming per-key isolation within the in-memory registry.
   *
   * @return void — asserts that only the marked event ID is reported as processed.
   */
  @Test
  void alreadyProcessed_differentEventIds_independentState() {
    service.markProcessed("evt-A");
    assertThat(service.alreadyProcessed("evt-A")).isTrue();
    assertThat(service.alreadyProcessed("evt-B")).isFalse();
  }

  /**
   * Verifies that passing {@code null} to {@link InMemoryIdempotencyService#alreadyProcessed}
   * raises a {@link NullPointerException}, enforcing the non-null event ID contract.
   *
   * @return void — asserts that a NullPointerException is thrown for a null event ID.
   */
  @Test
  void alreadyProcessed_nullEventId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.alreadyProcessed(null))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Verifies that passing {@code null} to {@link InMemoryIdempotencyService#markProcessed}
   * raises a {@link NullPointerException}, enforcing the non-null event ID contract.
   *
   * @return void — asserts that a NullPointerException is thrown when marking a null event ID.
   */
  @Test
  void markProcessed_nullEventId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.markProcessed(null))
        .isInstanceOf(NullPointerException.class);
  }
}
