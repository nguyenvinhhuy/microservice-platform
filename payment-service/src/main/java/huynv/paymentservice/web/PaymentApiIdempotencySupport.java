package huynv.paymentservice.web;

import huynv.paymentservice.exception.PaymentDomainException;
import org.springframework.util.StringUtils;

/**
 * Validates the canonical REST idempotency header used by payment-service.
 */
public final class PaymentApiIdempotencySupport {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private PaymentApiIdempotencySupport() {
    }

    /**
     * Resolves the canonical REST idempotency key from the dedicated HTTP header.
     *
     * @param headerIdempotencyKey Dedicated idempotency header value.
     * @return Returns the effective idempotency key used to deduplicate payment side effects.
     */
    public static String requireHttpIdempotencyKey(String headerIdempotencyKey) {
        if (StringUtils.hasText(headerIdempotencyKey)) {
            return headerIdempotencyKey.trim();
        }
        throw new PaymentDomainException("Missing idempotency key. Send Idempotency-Key header.");
    }
}

