package huynv.notificationservice.event;

import com.fasterxml.jackson.databind.node.NullNode;
import huynv.event.BaseEvent;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.notificationservice.service.NotificationProcessingService;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that upstream events are republished into the internal notification events topic.
 */
public class NotificationEventConsumerRepublishTest {

    /**
     * Republishes an upstream event to the internal topic and acknowledges offsets on success.
     *
     * @return Performs assertions via Mockito verification.
     */
    @Test
    public void shouldRepublishToInternalEventsTopic() {
        NotificationProcessingService processingService = Mockito.mock(NotificationProcessingService.class);
        NotificationMetrics metrics = Mockito.mock(NotificationMetrics.class);
        KafkaOutboxService outboxService = Mockito.mock(KafkaOutboxService.class);
        NotificationProperties properties = new NotificationProperties();
        properties.getKafka().setEventsTopic("notification.events");

        NotificationEventConsumer consumer = new NotificationEventConsumer(processingService, properties, outboxService, metrics, Tracer.NOOP);

        BaseEvent<NullNode> envelope = new BaseEvent<>(
                "evt-1",
                "order.created",
                "order-service",
                Instant.now(),
                "order-1",
                0L,
                "order.created.v1",
                "trace-1",
                "corr-1",
                null,
                NullNode.getInstance()
        );

        when(processingService.parseEnvelope(Mockito.anyString())).thenReturn((BaseEvent) envelope);
        Mockito.doNothing().when(processingService).validateRequiredFields(Mockito.any());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.events", 0, 0L, "k", "{\"eventId\":\"evt-1\"}");
        Acknowledgment acknowledgment = Mockito.mock(Acknowledgment.class);

        consumer.onOrderEvent(record, acknowledgment);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<KafkaOutboxPurpose> purposeCaptor = ArgumentCaptor.forClass(KafkaOutboxPurpose.class);
        ArgumentCaptor<OffsetDateTime> dueCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxService).enqueue(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture(), headersCaptor.capture(), purposeCaptor.capture(), dueCaptor.capture());
        verify(acknowledgment).acknowledge();

        org.assertj.core.api.Assertions.assertThat(topicCaptor.getValue()).isEqualTo("notification.events");
        org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue()).isEqualTo("unknown:order-1");
        org.assertj.core.api.Assertions.assertThat(purposeCaptor.getValue()).isEqualTo(KafkaOutboxPurpose.INTERNAL);
        org.assertj.core.api.Assertions.assertThat(headersCaptor.getValue()).containsKey("original_topic");
        org.assertj.core.api.Assertions.assertThat(headersCaptor.getValue().get("original_topic")).isEqualTo("order.events");
    }
}

