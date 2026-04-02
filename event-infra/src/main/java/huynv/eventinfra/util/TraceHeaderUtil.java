package huynv.eventinfra.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;

/**
 * Builds and propagates trace headers for Kafka messages when automatic instrumentation is disabled.
 */
public final class TraceHeaderUtil {

    private TraceHeaderUtil() {
    }

    /**
     * Adds trace identifiers and a W3C traceparent header into a mutable header map when traceId is present.
     *
     * @param headers Header map that will be enriched for downstream propagation.
     * @param traceId Trace identifier carried in the event envelope or job payload.
     * @param correlationId Correlation identifier used for business-flow grouping.
     * @param spanSeed Stable seed used to derive a synthetic parent span id for async propagation.
     * @return Performs a side effect by mutating the provided headers map.
     */
    public static void putTraceHeaders(Map<String, String> headers, String traceId, String correlationId, String spanSeed) {
        putTraceHeaders(headers, traceId, correlationId, spanSeed, null);
    }

    /**
     * Adds trace identifiers, W3C traceparent, and optional tracestate headers into a mutable header map.
     *
     * @param headers Header map that will be enriched for downstream propagation.
     * @param traceId Trace identifier carried in the event envelope or job payload.
     * @param correlationId Correlation identifier used for business-flow grouping.
     * @param spanSeed Stable seed used to derive a synthetic parent span id for async propagation.
     * @param tracestate Optional W3C tracestate header value to propagate when present.
     * @return Performs a side effect by mutating the provided headers map.
     */
    public static void putTraceHeaders(Map<String, String> headers,
                                       String traceId,
                                       String correlationId,
                                       String spanSeed,
                                       String tracestate) {
        Objects.requireNonNull(headers, "headers");
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        headers.putIfAbsent("traceId", traceId);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.putIfAbsent("correlationId", correlationId);
        }
        headers.putIfAbsent("traceparent", toTraceparent(traceId, spanSeed));
        if (tracestate != null && !tracestate.isBlank()) {
            headers.putIfAbsent("tracestate", tracestate.trim());
        }
    }

    /**
     * Builds a W3C traceparent header value using a trace id and a synthetic span id derived from stable input.
     *
     * @param traceId Trace identifier expected to be a 32-hex string.
     * @param spanSeed Seed used to derive a deterministic 16-hex span id value.
     * @return Returns a traceparent header value or null when traceId is invalid.
     */
    public static String toTraceparent(String traceId, String spanSeed) {
        String normalized = normalizeTraceId(traceId);
        if (normalized == null) {
            return null;
        }
        String spanId = toSyntheticSpanId(spanSeed);
        return "00-" + normalized + "-" + spanId + "-01";
    }

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

    private static String toSyntheticSpanId(String input) {
        if (input == null || input.isBlank()) {
            return "0000000000000000";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                int b = bytes[i] & 0xff;
                String part = Integer.toHexString(b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception ex) {
            return "0000000000000000";
        }
    }
}

