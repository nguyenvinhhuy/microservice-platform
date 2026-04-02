package huynv.paymentservice.service;

import huynv.event.idempotency.IdempotencyService;
import huynv.paymentservice.domain.ProcessedEvent;
import huynv.paymentservice.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implements consumer idempotency using the processed_events table and Spring Data repository access.
 */
@Component
public class ProcessedEventIdempotencyService implements IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;
    private final String consumerServiceName;

    /**
     * Creates a processed-events idempotency service for payment-service consumers.
     *
     * @param processedEventRepository Repository used to query and persist processed event markers.
     * @param consumerServiceName Consumer service name used to scope processed event markers.
     * @return Initializes an idempotency service implementation.
     */
    public ProcessedEventIdempotencyService(
            ProcessedEventRepository processedEventRepository,
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:payment-service}") String consumerServiceName
    ) {
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository, "processedEventRepository");
        this.consumerServiceName = consumerServiceName == null || consumerServiceName.isBlank() ? "payment-service" : consumerServiceName;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return processedEventRepository.existsByEventId(eventId);
    }

    @Override
    public void markProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        try {
            processedEventRepository.save(ProcessedEvent.of(eventId, consumerServiceName, OffsetDateTime.now()));
        } catch (DataIntegrityViolationException ignored) {
            // Another concurrent attempt already recorded the marker; consider the event processed.
        }
    }
}


