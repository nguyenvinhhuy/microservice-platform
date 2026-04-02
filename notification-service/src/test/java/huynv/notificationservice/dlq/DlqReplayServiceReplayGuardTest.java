package huynv.notificationservice.dlq;

import huynv.event.idempotency.IdempotencyService;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.eventinfra.dlq.DlqReplayService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that DLQ replay protection prevents infinite replay loops via an incrementing replay counter header.
 */
public class DlqReplayServiceReplayGuardTest {

    /**
     * Drops DLQ records when the replay counter exceeds the configured maximum.
     *
     * @return Performs assertions verifying the record is dropped and metrics are emitted.
     */
    @Test
    public void shouldDropWhenReplayCountExceedsMax() {
        NotificationProperties properties = new NotificationProperties();
        properties.getDlq().setMaxReplayAttempts(5);
        properties.getKafka().setEventsTopic("notification.events");

        KafkaOutboxService outboxService = Mockito.mock(KafkaOutboxService.class);
        IdempotencyService idempotencyService = Mockito.mock(IdempotencyService.class);
        NotificationMetrics metrics = Mockito.mock(NotificationMetrics.class);

        DlqReplayService service = new DlqReplayService(properties, outboxService, idempotencyService, metrics);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification.dlq", 0, 10L, "k", "v");
        record.headers().add("original_topic", "order.events".getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-replay-count", "5".getBytes(StandardCharsets.UTF_8));

        when(idempotencyService.alreadyProcessed(Mockito.anyString())).thenReturn(false);

        service.replayOnce(record);

        verify(outboxService, never()).enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any(KafkaOutboxPurpose.class), Mockito.any(OffsetDateTime.class));
        verify(metrics).incrementDlqReplayDroppedTotal("order.events", "notification.dlq");
        verify(idempotencyService).markProcessed(Mockito.anyString());
    }

    /**
     * Republishes DLQ records when below the maximum and increments the replay counter header.
     *
     * @return Performs assertions verifying the replay header is incremented on republish.
     */
    @Test
    public void shouldIncrementReplayCountHeaderOnReplay() {
        NotificationProperties properties = new NotificationProperties();
        properties.getDlq().setMaxReplayAttempts(5);
        properties.getKafka().setEventsTopic("notification.events");

        KafkaOutboxService outboxService = Mockito.mock(KafkaOutboxService.class);
        IdempotencyService idempotencyService = Mockito.mock(IdempotencyService.class);
        NotificationMetrics metrics = Mockito.mock(NotificationMetrics.class);

        DlqReplayService service = new DlqReplayService(properties, outboxService, idempotencyService, metrics);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification.dlq", 0, 10L, "k", "v");
        record.headers().add("original_topic", "order.events".getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-replay-count", "2".getBytes(StandardCharsets.UTF_8));

        when(idempotencyService.alreadyProcessed(Mockito.anyString())).thenReturn(false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService, never()).enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any(KafkaOutboxPurpose.class), Mockito.any(OffsetDateTime.class));

        service.replayOnce(record);

        verify(outboxService).enqueue(Mockito.eq("order.events"), Mockito.eq("k"), Mockito.eq("v"), headersCaptor.capture(), Mockito.eq(KafkaOutboxPurpose.DLQ_REPLAY), Mockito.any(OffsetDateTime.class));
        assertThat(headersCaptor.getValue()).containsEntry("x-replay-count", "3");
        verify(metrics).incrementDlqReplay("order.events");
        verify(idempotencyService).markProcessed(Mockito.anyString());
    }
}


