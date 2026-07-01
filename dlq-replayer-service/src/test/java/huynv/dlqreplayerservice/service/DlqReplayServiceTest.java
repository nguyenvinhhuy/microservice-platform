package huynv.dlqreplayerservice.service;

import huynv.dlqreplayerservice.model.DlqEvent;
import huynv.dlqreplayerservice.model.DlqEventStatus;
import huynv.dlqreplayerservice.repository.DlqEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqReplayServiceTest {

  @Mock private DlqEventRepository dlqEventRepository;
  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private DlqReplayService service;

  /**
   * Initializes the service under test with mock repository and Kafka template dependencies before each test.
   *
   * @return Creates a fresh DlqReplayService instance wired to the mocked repository and Kafka template.
   */
  @BeforeEach
  void setUp() {
    service = new DlqReplayService(dlqEventRepository, kafkaTemplate);
  }

  // -----------------------------------------------------------------------
  // replay
  // -----------------------------------------------------------------------

  /**
   * Verifies that replaying a DLQ event without an override topic publishes to the stored original topic and marks the event as REPLAYED.
   *
   * @return Asserts that KafkaTemplate.send is called with the original topic and that the event status is updated to REPLAYED.
   */
  @Test
  void replay_withOriginalTopic_publishesToOriginalTopicAndMarksReplayed() {
    DlqEvent event = buildEvent("order.events.dlq", "order.events", "key-1", "{\"orderId\":\"abc\"}");
    when(dlqEventRepository.findById(1L)).thenReturn(Optional.of(event));

    service.replay(1L, null);

    verify(kafkaTemplate).send("order.events", "key-1", "{\"orderId\":\"abc\"}");
    assertThat(event.getStatus()).isEqualTo(DlqEventStatus.REPLAYED);
    verify(dlqEventRepository).save(event);
  }

  /**
   * Verifies that replaying a DLQ event with a non-blank override topic publishes to the override topic instead of the stored original.
   *
   * @return Asserts that KafkaTemplate.send is called with the override topic and that the event status is updated to REPLAYED.
   */
  @Test
  void replay_withOverrideTopic_publishesToOverrideTopicIgnoringOriginal() {
    DlqEvent event = buildEvent("order.events.dlq", "order.events", "key-2", "payload");
    when(dlqEventRepository.findById(2L)).thenReturn(Optional.of(event));

    service.replay(2L, "override.topic");

    verify(kafkaTemplate).send("override.topic", "key-2", "payload");
    assertThat(event.getStatus()).isEqualTo(DlqEventStatus.REPLAYED);
  }

  /**
   * Verifies that a blank (whitespace-only) override topic is treated as absent, causing replay to fall back to the stored original topic.
   *
   * @return Asserts that KafkaTemplate.send is called with the original topic when the override topic string is blank.
   */
  @Test
  void replay_blankOverrideTopic_fallsBackToOriginalTopic() {
    DlqEvent event = buildEvent("payment.events.dlq", "payment.events", "key-3", "data");
    when(dlqEventRepository.findById(3L)).thenReturn(Optional.of(event));

    service.replay(3L, "   ");

    verify(kafkaTemplate).send("payment.events", "key-3", "data");
  }

  /**
   * Verifies that replay throws an IllegalStateException when the event has no original topic and the override is null.
   *
   * @return Asserts that an IllegalStateException is thrown containing the event ID, and that neither Kafka nor the repository is mutated.
   */
  @Test
  void replay_noOriginalTopicAndNullOverride_throwsIllegalStateException() {
    DlqEvent event = buildEvent("some.dlq", null, "key-4", "data");
    when(dlqEventRepository.findById(4L)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> service.replay(4L, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("4");

    verify(kafkaTemplate, never()).send(any(), any(), any());
    verify(dlqEventRepository, never()).save(any());
  }

  /**
   * Verifies that replay throws an IllegalStateException when the event has no original topic and the override topic is blank.
   *
   * @return Asserts that an IllegalStateException is thrown and that neither Kafka nor the repository is mutated.
   */
  @Test
  void replay_noOriginalTopicAndBlankOverride_throwsIllegalStateException() {
    DlqEvent event = buildEvent("some.dlq", null, "key-5", "data");
    when(dlqEventRepository.findById(5L)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> service.replay(5L, "  "))
        .isInstanceOf(IllegalStateException.class);

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  /**
   * Verifies that replay throws a NoSuchElementException when no DLQ event exists for the given identifier.
   *
   * @return Asserts that a NoSuchElementException is thrown and that Kafka is never invoked for a missing event.
   */
  @Test
  void replay_eventNotFound_throwsNoSuchElementException() {
    when(dlqEventRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replay(99L, null))
        .isInstanceOf(NoSuchElementException.class);

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  /**
   * Verifies that replay throws a NullPointerException when a null event identifier is supplied.
   *
   * @return Asserts that a NullPointerException is thrown immediately when the id argument is null.
   */
  @Test
  void replay_nullId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.replay(null, "topic"))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // skip
  // -----------------------------------------------------------------------

  /**
   * Verifies that skipping an existing DLQ event updates its status to SKIPPED and persists the change.
   *
   * @return Asserts that the event status is set to SKIPPED and that the repository save method is invoked.
   */
  @Test
  void skip_existingEvent_marksSkippedAndSaves() {
    DlqEvent event = buildEvent("inventory.events.dlq", "inventory.events", "key-6", "payload");
    when(dlqEventRepository.findById(6L)).thenReturn(Optional.of(event));

    service.skip(6L);

    assertThat(event.getStatus()).isEqualTo(DlqEventStatus.SKIPPED);
    verify(dlqEventRepository).save(event);
  }

  /**
   * Verifies that skipping a non-existent DLQ event throws a NoSuchElementException.
   *
   * @return Asserts that a NoSuchElementException is thrown when the repository returns an empty Optional for the given id.
   */
  @Test
  void skip_eventNotFound_throwsNoSuchElementException() {
    when(dlqEventRepository.findById(88L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.skip(88L))
        .isInstanceOf(NoSuchElementException.class);
  }

  /**
   * Verifies that skipping with a null event identifier throws a NullPointerException.
   *
   * @return Asserts that a NullPointerException is thrown immediately when the id argument is null.
   */
  @Test
  void skip_nullId_throwsNullPointerException() {
    assertThatThrownBy(() -> service.skip(null))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

  /**
   * Constructs a DlqEvent with the given topic metadata and payload for use as test fixture data.
   *
   * @param topic         The DLQ topic from which the event was consumed.
   * @param originalTopic The topic to which the event should be replayed, or null if not known.
   * @param key           The Kafka message key associated with the event.
   * @param payload       The raw JSON payload of the event.
   * @return A new DlqEvent instance in PENDING status with the supplied fields and partition 0, offset 1.
   */
  private DlqEvent buildEvent(String topic, String originalTopic, String key, String payload) {
    DlqEvent e = new DlqEvent();
    e.setTopic(topic);
    e.setPartition(0);
    e.setOffset(1L);
    e.setKey(key);
    e.setPayload(payload);
    e.setOriginalTopic(originalTopic);
    e.setStatus(DlqEventStatus.PENDING);
    return e;
  }
}
