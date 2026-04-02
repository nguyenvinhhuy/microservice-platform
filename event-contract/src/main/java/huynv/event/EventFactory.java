package huynv.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Creates unified event envelopes with deterministic identifiers and caller-provided correlation fields.
 */
public final class EventFactory {

    private final String source;
    private final Clock clock;
    private final Supplier<String> traceIdSupplier;

    /**
     * Creates an event factory for a given service source name.
     *
     * @param source Service name published in the event envelope.
     * @return Initializes an event factory instance.
     */
    public EventFactory(String source) {
        this(source, Clock.systemUTC(), () -> null);
    }

    /**
     * Creates an event factory using a caller-supplied trace identifier source.
     *
     * @param source Service name published in the event envelope.
     * @param traceIdSupplier Supplier used to obtain the current trace identifier.
     * @return Initializes an event factory instance.
     */
    public EventFactory(String source, Supplier<String> traceIdSupplier) {
        this(source, Clock.systemUTC(), traceIdSupplier);
    }

    /**
     * Creates an event factory with explicit clock and trace supplier for deterministic testing.
     *
     * @param source Service name published in the event envelope.
     * @param clock Clock used to read eventTime values.
     * @param traceIdSupplier Supplier used to obtain the current trace identifier.
     * @return Initializes an event factory instance.
     */
    public EventFactory(String source, Clock clock, Supplier<String> traceIdSupplier) {
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.traceIdSupplier = Objects.requireNonNull(traceIdSupplier, "traceIdSupplier");
    }

    /**
     * Creates a unified event envelope for a given aggregate using the current time and a generated ULID identifier.
     *
     * @param eventType Event type string used by consumers.
     * @param aggregateId Aggregate identifier used for partitioning and routing.
     * @param aggregateVersion Aggregate version at publish time.
     * @param dataSchema Schema identifier with version suffix.
     * @param correlationId Correlation identifier for one business flow.
     * @param causationId Causation identifier representing the triggering event or command.
     * @param data Domain payload to embed under the data field.
     * @return Returns a new BaseEvent instance with generated eventId and current eventTime.
     */
    public <T> BaseEvent<T> create(String eventType,
                                   String aggregateId,
                                   long aggregateVersion,
                                   String dataSchema,
                                   String correlationId,
                                   String causationId,
                                   T data) {
        Instant now = Instant.now(clock);
        String traceId = traceIdSupplier.get();
        return new BaseEvent<>(
                UlidGenerator.nextUlid(now),
                eventType,
                source,
                now,
                aggregateId,
                aggregateVersion,
                dataSchema,
                traceId,
                correlationId,
                causationId,
                data
        );
    }

    /**
     * Creates a unified event envelope using explicit metadata and caller-provided payload.
     *
     * @param metadata Metadata describing the event.
     * @param data Domain payload to embed under the data field.
     * @param <T> Payload type stored under the data field.
     * @return Returns a new BaseEvent instance with generated eventId.
     */
    public <T> BaseEvent<T> create(EventMetadata metadata, T data) {
        Objects.requireNonNull(metadata, "metadata");
        return new BaseEvent<>(
                UlidGenerator.nextUlid(metadata.eventTime()),
                metadata.eventType(),
                metadata.source(),
                metadata.eventTime(),
                metadata.aggregateId(),
                metadata.aggregateVersion(),
                metadata.dataSchema(),
                metadata.traceId(),
                metadata.correlationId(),
                metadata.causationId(),
                data
        );
    }
}

