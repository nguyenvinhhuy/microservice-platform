package huynv.productservice.web;

import java.util.UUID;

/**
 * Resolves the effective idempotency key for product create requests while preserving legacy compatibility.
 */
public final class ProductIdempotencyKeyResolver {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private ProductIdempotencyKeyResolver() {
    }

    /**
     * Resolves the preferred create-command idempotency key, falling back to the legacy request id when needed.
     *
     * @param idempotencyKeyHeader Dedicated idempotency key from the preferred header.
     * @param requestIdHeader Legacy request id reused by older callers for create deduplication.
     * @return Returns the effective create-command idempotency key or null when deduplication is not requested.
     */
    public static UUID resolve(UUID idempotencyKeyHeader, UUID requestIdHeader) {
        if (idempotencyKeyHeader != null) {
            return idempotencyKeyHeader;
        }
        return requestIdHeader;
    }
}

