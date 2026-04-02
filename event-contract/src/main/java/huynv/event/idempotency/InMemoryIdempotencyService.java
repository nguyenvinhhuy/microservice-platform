package huynv.event.idempotency;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a best-effort idempotency implementation for environments without a database.
 */
public final class InMemoryIdempotencyService implements IdempotencyService {

    private final ConcurrentHashMap<String, Instant> processed = new ConcurrentHashMap<>();

    /**
     * Creates an in-memory idempotency service suitable for tests and non-production profiles.
     *
     * @return Initializes the in-memory service.
     */
    public InMemoryIdempotencyService() {
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return processed.containsKey(eventId);
    }

    @Override
    public void markProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        processed.put(eventId, Instant.now());
    }
}


