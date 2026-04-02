package huynv.event;

import java.time.Instant;

/**
 * Captures metadata required to build a unified Kafka event envelope.
 *
 * @param eventType Event type string used by consumers.
 * @param source Service identifier published in the event envelope.
 * @param eventTime Event time in UTC used for ordering and diagnostics.
 * @param aggregateId Aggregate identifier used for partitioning and routing.
 * @param aggregateVersion Aggregate version used for consumer ordering invariants.
 * @param dataSchema Schema identifier with explicit version suffix.
 * @param traceId Distributed trace identifier propagated across services.
 * @param correlationId Correlation identifier for one business flow.
 * @param causationId Causation identifier representing the triggering event or command.
 * @return Returns an immutable metadata record used to build event envelopes.
 */
public record EventMetadata(
        String eventType,
        String source,
        Instant eventTime,
        String aggregateId,
        long aggregateVersion,
        String dataSchema,
        String traceId,
        String correlationId,
        String causationId
) {
}

