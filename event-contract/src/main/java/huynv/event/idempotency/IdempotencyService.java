package huynv.event.idempotency;

/**
 * Defines a consumer-side idempotency API backed by the processed_events table contract.
 */
public interface IdempotencyService {

    /**
     * Determines whether an event identifier has already been processed by the current consumer.
     *
     * @param eventId Event identifier to check for duplicates.
     * @return Returns true when the event identifier has already been recorded as processed.
     */
    boolean alreadyProcessed(String eventId);

    /**
     * Records the event identifier as processed for the current consumer.
     *
     * @param eventId Event identifier to record as processed.
     * @return Performs a side effect by persisting the processed marker.
     */
    void markProcessed(String eventId);
}


