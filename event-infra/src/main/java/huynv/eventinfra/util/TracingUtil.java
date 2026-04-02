package huynv.eventinfra.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides helper methods for creating Micrometer tracing spans from upstream correlation identifiers.
 */
public final class TracingUtil {

    private TracingUtil() {
    }

    /**
     * Starts a span with an optional parent derived from a provided trace identifier.
     *
     * @param tracer Tracer used to create spans.
     * @param spanName Span name to assign.
     * @param traceId Upstream trace identifier to adopt as the parent trace when valid.
     * @return Returns a started span that must be ended by the caller.
     */
    public static Span startSpan(Tracer tracer, String spanName, String traceId) {
        Objects.requireNonNull(tracer, "tracer");
        Objects.requireNonNull(spanName, "spanName");

        if (tracer.currentSpan() != null) {
            return tracer.spanBuilder().name(spanName).start();
        }

        TraceContext parent = parentContextOrNull(tracer, traceId);
        if (parent == null) {
            return tracer.spanBuilder().name(spanName).start();
        }
        return tracer.spanBuilder().setParent(parent).name(spanName).start();
    }

    /**
     * Attempts to build a parent trace context from a trace identifier string.
     *
     * @param tracer Tracer used to create trace contexts.
     * @param traceId Trace identifier candidate.
     * @return Returns a TraceContext when the trace identifier is valid.
     */
    private static TraceContext parentContextOrNull(Tracer tracer, String traceId) {
        Objects.requireNonNull(tracer, "tracer");
        String normalized = normalizeTraceId(traceId);
        if (normalized == null) {
            return null;
        }
        return tracer.traceContextBuilder()
                .traceId(normalized)
                .spanId(randomSpanId())
                .sampled(Boolean.TRUE)
                .build();
    }

    /**
     * Normalizes a trace identifier to a 32-hex W3C compatible trace id when possible.
     *
     * @param traceId Trace identifier candidate.
     * @return Returns a normalized trace id or null when invalid.
     */
    private static String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String hex = trimmed.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (hex.length() != 32) {
            return null;
        }
        return hex;
    }

    /**
     * Generates a random 16-hex span identifier.
     *
     * @return Returns a random span id as 16 lowercase hex characters.
     */
    private static String randomSpanId() {
        long value = ThreadLocalRandom.current().nextLong();
        String hex = Long.toHexString(value);
        if (hex.length() >= 16) {
            return hex.substring(hex.length() - 16);
        }
        return "0".repeat(16 - hex.length()) + hex;
    }
}

