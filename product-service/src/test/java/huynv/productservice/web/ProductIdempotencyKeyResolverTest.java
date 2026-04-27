package huynv.productservice.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductIdempotencyKeyResolverTest {

    @Test
    void shouldPreferDedicatedIdempotencyHeader() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        UUID resolved = ProductIdempotencyKeyResolver.resolve(idempotencyKey, requestId);

        assertEquals(idempotencyKey, resolved);
    }

    @Test
    void shouldFallbackToLegacyRequestId() {
        UUID requestId = UUID.randomUUID();

        UUID resolved = ProductIdempotencyKeyResolver.resolve(null, requestId);

        assertEquals(requestId, resolved);
    }

    @Test
    void shouldAllowCreateWithoutIdempotencyKey() {
        UUID resolved = ProductIdempotencyKeyResolver.resolve(null, null);

        assertNull(resolved);
    }
}

