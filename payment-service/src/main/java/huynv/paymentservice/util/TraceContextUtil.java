package huynv.paymentservice.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Provides helper methods for extracting trace identifiers from the current OpenTelemetry context.
 */
public final class TraceContextUtil {

    private TraceContextUtil() {
    }

    /**
     * Returns the current trace context identifiers when available.
     *
     * @return Returns a TraceIds object containing traceId and spanId, or null when not available.
     */
    public static TraceIds currentTraceIdsOrNull() {
        SpanContext context = Span.current().getSpanContext();
        if (!context.isValid()) {
            return null;
        }
        return new TraceIds(context.getTraceId(), context.getSpanId());
    }

    /**
     * Represents a pair of traceId and spanId identifiers.
     */
    public record TraceIds(String traceId, String spanId) {
    }
}

