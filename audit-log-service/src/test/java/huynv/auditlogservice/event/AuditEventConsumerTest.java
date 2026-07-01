package huynv.auditlogservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.auditlogservice.service.AuditLogService;
import huynv.event.idempotency.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

  @Mock private IdempotencyService idempotencyService;
  @Mock private AuditLogService auditLogService;
  @Mock private Acknowledgment acknowledgment;

  private AuditEventConsumer consumer;

  // eventTime omitted intentionally — JavaTimeModule not on test classpath
  private static final String VALID_PAYLOAD =
      "{\"eventId\":\"evt-001\",\"eventType\":\"order.created\",\"source\":\"order-service\","
          + "\"aggregateId\":\"order-123\",\"aggregateVersion\":1,\"correlationId\":\"corr-001\","
          + "\"causationId\":\"caus-001\",\"data\":{\"tenantId\":1,\"userId\":2}}";

  /**
   * Initializes the consumer under test with real ObjectMapper and mock dependencies before each test.
   *
   * @return Creates a fresh AuditEventConsumer instance wired to the mocked idempotency and audit log services.
   */
  @BeforeEach
  void setUp() {
    consumer = new AuditEventConsumer(new ObjectMapper(), idempotencyService, auditLogService);
  }

  /**
   * Verifies that a Kafka record with a null payload is acknowledged without invoking the audit log service.
   *
   * @return Asserts that acknowledgment is called and auditLogService.record is never invoked.
   */
  @Test
  void onOrderEvent_nullPayload_acknowledgesWithoutRecording() {
    consumer.onOrderEvent(record(null), acknowledgment);

    verify(acknowledgment).acknowledge();
    verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  /**
   * Verifies that a Kafka record with a blank (whitespace-only) payload is acknowledged without invoking the audit log service.
   *
   * @return Asserts that acknowledgment is called and auditLogService.record is never invoked.
   */
  @Test
  void onOrderEvent_blankPayload_acknowledgesWithoutRecording() {
    consumer.onOrderEvent(record("   "), acknowledgment);

    verify(acknowledgment).acknowledge();
    verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  /**
   * Verifies that a Kafka record whose JSON payload lacks an eventId field is acknowledged without invoking the audit log service.
   *
   * @return Asserts that acknowledgment is called and auditLogService.record is never invoked when eventId is absent.
   */
  @Test
  void onOrderEvent_missingEventId_acknowledgesWithoutRecording() {
    // eventTime omitted — JavaTimeModule not on test classpath
    String payloadNoId =
        "{\"eventType\":\"order.created\",\"source\":\"order-service\","
            + "\"aggregateId\":\"order-123\",\"data\":{}}";

    consumer.onOrderEvent(record(payloadNoId), acknowledgment);

    verify(acknowledgment).acknowledge();
    verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  /**
   * Verifies that a Kafka record whose eventId has already been processed is acknowledged without re-recording the audit log.
   *
   * @return Asserts that acknowledgment is called and auditLogService.record is never invoked for a duplicate event.
   */
  @Test
  void onOrderEvent_alreadyProcessed_skipsRecordingAndAcknowledges() {
    when(idempotencyService.alreadyProcessed("evt-001")).thenReturn(true);

    consumer.onOrderEvent(record(VALID_PAYLOAD), acknowledgment);

    verify(acknowledgment).acknowledge();
    verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  /**
   * Verifies that a valid, previously-unseen order event is recorded, marked as processed, and acknowledged.
   *
   * @return Asserts that auditLogService.record is called with the correct event fields, idempotency is marked, and acknowledgment fires.
   */
  @Test
  void onOrderEvent_happyPath_recordsAndMarksProcessedAndAcknowledges() {
    when(idempotencyService.alreadyProcessed("evt-001")).thenReturn(false);

    consumer.onOrderEvent(record(VALID_PAYLOAD), acknowledgment);

    verify(auditLogService)
        .record(
            "evt-001",
            "order.created",
            "order-service",
            1L,
            2L,
            "order-123",
            "corr-001",
            "caus-001",
            VALID_PAYLOAD);
    verify(idempotencyService).markProcessed("evt-001");
    verify(acknowledgment).acknowledge();
  }

  /**
   * Verifies that a Kafka record containing malformed JSON causes the consumer to throw an IllegalStateException.
   *
   * @return Asserts that an IllegalStateException is thrown when the payload cannot be parsed as valid JSON.
   */
  @Test
  void onOrderEvent_invalidJson_throwsIllegalStateException() {
    assertThatThrownBy(() -> consumer.onOrderEvent(record("not-valid-json"), acknowledgment))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Verifies that a valid payment event is handled by the same recording logic used for order events.
   *
   * @return Asserts that auditLogService.record is called and acknowledgment fires for a new payment event.
   */
  @Test
  void onPaymentEvent_happyPath_delegatesToHandleLogic() {
    when(idempotencyService.alreadyProcessed("evt-001")).thenReturn(false);

    consumer.onPaymentEvent(record(VALID_PAYLOAD), acknowledgment);

    verify(auditLogService).record(anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyString());
    verify(acknowledgment).acknowledge();
  }

  /**
   * Creates a minimal ConsumerRecord on the order.events topic with the given payload for use in tests.
   *
   * @param payload The Kafka message value to embed in the returned record.
   * @return A ConsumerRecord with partition 0, offset 0, key "key-1", and the supplied payload.
   */
  private ConsumerRecord<String, String> record(String payload) {
    return new ConsumerRecord<>("order.events", 0, 0L, "key-1", payload);
  }
}
