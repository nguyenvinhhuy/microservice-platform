package huynv.notificationservice.event;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.BaseEvent;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.notificationservice.service.NotificationIngestionService;
import huynv.notificationservice.service.NotificationProcessingService;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the internal events consumer skips already processed events and acknowledges offsets.
 */
public class NotificationInternalEventConsumerIdempotencyTest {

    /**
     * Skips duplicate events and acknowledges without dispatching notifications.
     *
     * @return Performs assertions via Mockito verification.
     */
    @Test
    public void shouldSkipAlreadyProcessedEvent() {
        NotificationProcessingService processingService = Mockito.mock(NotificationProcessingService.class);
        NotificationIngestionService ingestionService = Mockito.mock(NotificationIngestionService.class);
        IdempotencyService idempotencyService = Mockito.mock(IdempotencyService.class);
        NotificationMetrics metrics = Mockito.mock(NotificationMetrics.class);
        NotificationInternalEventConsumer consumer = new NotificationInternalEventConsumer(processingService, ingestionService, idempotencyService, metrics, Tracer.NOOP);

        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("tenantId", 1);
        BaseEvent<ObjectNode> envelope = new BaseEvent<>(
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
                data
        );

        when(processingService.parseEnvelope(Mockito.anyString())).thenReturn((BaseEvent) envelope);
        Mockito.doNothing().when(processingService).validateRequiredFields(Mockito.any());
        when(idempotencyService.alreadyProcessed("evt-1")).thenReturn(true);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification.events", 0, 0L, "k", "{\"eventId\":\"evt-1\"}");
        Acknowledgment acknowledgment = Mockito.mock(Acknowledgment.class);

        consumer.onInternalEvent(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(ingestionService, never()).ingest(Mockito.anyString());
        verify(idempotencyService, never()).markProcessed(Mockito.anyString());
    }
}


