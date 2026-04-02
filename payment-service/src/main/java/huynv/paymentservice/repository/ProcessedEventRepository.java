package huynv.paymentservice.repository;

import huynv.paymentservice.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Provides persistence access for processed event markers used for consumer idempotency.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Checks whether a given event identifier has already been processed by this consumer service.
     *
     * @param eventId Event identifier from the inbound Kafka message.
     * @return Returns true when the event has already been processed.
     */
    boolean existsByEventId(String eventId);
}
