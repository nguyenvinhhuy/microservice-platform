package huynv.eventinfra.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MdcUtilTest {

  /**
   * Clears the SLF4J MDC after each test to prevent MDC state from leaking between tests.
   *
   * @return void — removes all entries from the MDC context map.
   */
  @AfterEach
  void cleanUp() {
    MDC.clear();
  }

  /**
   * Verifies that calling {@link MdcUtil#putAll} with a {@code null} map does not throw
   * any exception, treating null as a no-op to allow safe use in error-handling paths.
   *
   * @return void — asserts that no exception is thrown when the input map is null.
   */
  @Test
  void putAll_nullMap_noExceptionThrown() {
    assertThatCode(() -> MdcUtil.putAll(null)).doesNotThrowAnyException();
  }

  /**
   * Verifies that calling {@link MdcUtil#putAll} with an empty map does not throw any
   * exception, confirming that the method handles empty inputs gracefully.
   *
   * @return void — asserts that no exception is thrown when the input map is empty.
   */
  @Test
  void putAll_emptyMap_noExceptionThrown() {
    assertThatCode(() -> MdcUtil.putAll(Map.of())).doesNotThrowAnyException();
  }

  /**
   * Verifies that {@link MdcUtil#putAll} writes each key-value pair from the supplied map
   * into the SLF4J MDC, making them available for structured log enrichment.
   *
   * @return void — asserts that all supplied keys are present with the correct values in the MDC.
   */
  @Test
  void putAll_populatesExpectedMdcKeys() {
    Map<String, String> values = Map.of("tenantId", "1", "orderId", "ord-99");
    MdcUtil.putAll(values);
    assertThat(MDC.get("tenantId")).isEqualTo("1");
    assertThat(MDC.get("orderId")).isEqualTo("ord-99");
  }

  /**
   * Verifies that a {@code null} value in the map passed to {@link MdcUtil#putAll} causes
   * the corresponding MDC key to be removed, preventing stale diagnostic context from
   * propagating into log statements.
   *
   * @return void — asserts that a key mapped to null is absent from the MDC after the call.
   */
  @Test
  void putAll_nullValueForKey_removesKeyFromMdc() {
    MDC.put("eventId", "old-value");
    Map<String, String> values = new HashMap<>();
    values.put("eventId", null);
    MdcUtil.putAll(values);
    assertThat(MDC.get("eventId")).isNull();
  }

  /**
   * Verifies that {@link MdcUtil#clear} removes all entries from the SLF4J MDC, ensuring
   * that diagnostic context does not leak across request or event processing boundaries.
   *
   * @return void — asserts that the MDC context map is null or empty after the clear call.
   */
  @Test
  void clear_removesAllMdcKeys() {
    MDC.put("tenantId", "1");
    MDC.put("correlationId", "abc");
    MdcUtil.clear();
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }
}
