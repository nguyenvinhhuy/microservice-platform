package huynv.orderservice.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandIdempotencyKeyResolverTest {

    @Test
    void shouldPreferDedicatedIdempotencyHeader() {
        String resolved = CommandIdempotencyKeyResolver.require("idem-123", "req-456");

        assertEquals("idem-123", resolved);
    }

    @Test
    void shouldFallbackToLegacyRequestIdWhenDedicatedHeaderMissing() {
        String resolved = CommandIdempotencyKeyResolver.require(null, "req-456");

        assertEquals("req-456", resolved);
    }

    @Test
    void shouldRejectMissingCommandKey() {
        assertThrows(IllegalArgumentException.class, () -> CommandIdempotencyKeyResolver.require("  ", null));
    }
}

