package huynv.paymentservice.web;

import huynv.paymentservice.exception.PaymentDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentApiIdempotencySupportTest {

    @Test
    void shouldPreferDedicatedIdempotencyHeader() {
        String resolved = PaymentApiIdempotencySupport.requireHttpIdempotencyKey("idem-123");

        assertEquals("idem-123", resolved);
    }

    @Test
    void shouldRejectMissingKey() {
        assertThrows(PaymentDomainException.class, () -> PaymentApiIdempotencySupport.requireHttpIdempotencyKey("  "));
    }
}

