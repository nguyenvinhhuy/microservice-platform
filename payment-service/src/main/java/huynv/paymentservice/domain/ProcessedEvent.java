package huynv.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Stores consumer-side processed event markers to guarantee idempotent event consumption.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "consumer_service", nullable = false, updatable = false, length = 100)
    private String consumerService;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    /**
     * Creates an empty JPA entity required by Hibernate.
     *
     * @return Initializes an empty processed event entity instance.
     */
    protected ProcessedEvent() {
    }

    /**
     * Creates a processed event marker for consumer idempotency.
     *
     * @param eventId Unique event identifier from Kafka payload.
     * @param consumerService Consumer service name to scope idempotency.
     * @param processedAt Timestamp when the event was processed.
     * @return Initializes a processed event marker entity.
     */
    public static ProcessedEvent of(String eventId, String consumerService, OffsetDateTime processedAt) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.eventId = Objects.requireNonNull(eventId, "eventId");
        processedEvent.consumerService = Objects.requireNonNull(consumerService, "consumerService");
        processedEvent.processedAt = Objects.requireNonNull(processedAt, "processedAt");
        return processedEvent;
    }
}
