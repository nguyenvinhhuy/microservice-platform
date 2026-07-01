package huynv.event;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventFactoryTest {

  /**
   * Verifies that the {@code source} field of a created event matches the service name
   * supplied to the {@link EventFactory} constructor.
   *
   * @return void — asserts that the event envelope carries the correct source identifier.
   */
  @Test
  void create_setsSourceFromConstructor() {
    EventFactory factory = new EventFactory("order-service");
    BaseEvent<String> event =
        factory.create("order.created", "order-1", 1L, "order.created.v1", "corr", "caus", "data");
    assertThat(event.source()).isEqualTo("order-service");
  }

  /**
   * Verifies that the {@code eventType}, {@code aggregateId}, and {@code aggregateVersion} fields
   * are populated from the arguments passed to {@link EventFactory#create}.
   *
   * @return void — asserts that all three envelope routing fields match the supplied values.
   */
  @Test
  void create_setsEventTypeAndAggregateId() {
    EventFactory factory = new EventFactory("order-service");
    BaseEvent<String> event =
        factory.create("order.cancelled", "order-99", 2L, "order.cancelled.v1", null, null, null);
    assertThat(event.eventType()).isEqualTo("order.cancelled");
    assertThat(event.aggregateId()).isEqualTo("order-99");
    assertThat(event.aggregateVersion()).isEqualTo(2L);
  }

  /**
   * Verifies that {@link EventFactory#create} auto-generates a non-blank ULID of exactly 26
   * characters as the event identifier, rather than requiring the caller to supply one.
   *
   * @return void — asserts that the generated event ID is non-blank and 26 characters long.
   */
  @Test
  void create_generatesNonBlankEventId() {
    EventFactory factory = new EventFactory("payment-service");
    BaseEvent<Void> event =
        factory.create("payment.completed", "pay-1", 1L, "payment.completed.v1", null, null, null);
    assertThat(event.eventId()).isNotBlank().hasSize(26);
  }

  /**
   * Verifies that when an {@link EventFactory} is constructed with a fixed {@link Clock},
   * the resulting event's {@code eventTime} matches that fixed instant rather than wall time.
   *
   * @return void — asserts that the event timestamp equals the fixed clock instant.
   */
  @Test
  void create_usesFixedClockForEventTime() {
    Instant fixed = Instant.parse("2024-01-15T10:00:00Z");
    EventFactory factory = new EventFactory("test-service", Clock.fixed(fixed, ZoneOffset.UTC), () -> null);
    BaseEvent<Void> event =
        factory.create("test.event", "agg-1", 1L, "test.event.v1", null, null, null);
    assertThat(event.eventTime()).isEqualTo(fixed);
  }

  /**
   * Verifies that the trace ID returned by the supplier injected into the {@link EventFactory}
   * constructor is propagated unchanged into the {@code traceId} field of the event envelope.
   *
   * @return void — asserts that the event's traceId equals the value produced by the supplier.
   */
  @Test
  void create_traceIdSupplierResult_propagatedToEnvelope() {
    EventFactory factory = new EventFactory("svc", () -> "trace-abc-123");
    BaseEvent<Void> event =
        factory.create("user.created", "user-1", 1L, "user.created.v1", "corr", "caus", null);
    assertThat(event.traceId()).isEqualTo("trace-abc-123");
  }

  /**
   * Verifies that constructing an {@link EventFactory} with a {@code null} source name
   * throws a {@link NullPointerException} immediately, enforcing the non-null contract.
   *
   * @return void — asserts that a NullPointerException is raised for a null source argument.
   */
  @Test
  void constructor_nullSource_throwsNullPointerException() {
    assertThatThrownBy(() -> new EventFactory(null)).isInstanceOf(NullPointerException.class);
  }
}
