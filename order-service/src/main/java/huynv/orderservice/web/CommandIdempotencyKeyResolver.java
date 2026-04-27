package huynv.orderservice.web;

import org.springframework.util.StringUtils;

/**
 * Resolves the effective idempotency key for mutating HTTP commands while preserving legacy compatibility.
 */
public final class CommandIdempotencyKeyResolver {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private CommandIdempotencyKeyResolver() {
    }

    /**
     * Resolves the idempotency key from the preferred dedicated header and falls back to the legacy request id.
     *
     * @param idempotencyKeyHeader Dedicated idempotency header value for the command when provided.
     * @param requestIdHeader Legacy request id header value used by older callers.
     * @return Returns the effective idempotency key for command deduplication.
     */
    public static String require(String idempotencyKeyHeader, String requestIdHeader) {
        if (StringUtils.hasText(idempotencyKeyHeader)) {
            return idempotencyKeyHeader.trim();
        }
        if (StringUtils.hasText(requestIdHeader)) {
            return requestIdHeader.trim();
        }
        throw new IllegalArgumentException("Missing required header Idempotency-Key");
    }
}

