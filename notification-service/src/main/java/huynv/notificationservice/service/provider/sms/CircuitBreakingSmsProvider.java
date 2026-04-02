package huynv.notificationservice.service.provider.sms;

import huynv.notificationservice.service.provider.ProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Wraps an SmsProvider with a Resilience4j circuit breaker for external dependency protection.
 */
@Component
@Primary
public class CircuitBreakingSmsProvider implements SmsProvider {

    private final SmsProvider delegate;
    private final CircuitBreaker circuitBreaker;

    /**
     * Creates a circuit-breaking wrapper around the configured SMS provider implementation.
     *
     * @param delegate Underlying SMS provider implementation.
     * @param circuitBreakerRegistry Circuit breaker registry used to obtain provider circuit breaker instances.
     * @return Initializes a circuit-breaking SMS provider wrapper.
     */
    public CircuitBreakingSmsProvider(
            @Qualifier("simulatedSmsProvider") SmsProvider delegate,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("smsProvider");
    }

    /**
     * Sends an SMS request through the delegate provider while rejecting calls when the circuit breaker is open.
     *
     * @param request Request to send through the underlying provider.
     * @return Performs side effects by delivering an SMS when allowed by the circuit breaker.
     */
    @Override
    public void send(SmsSendRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            circuitBreaker.executeRunnable(() -> delegate.send(request));
        } catch (CallNotPermittedException ex) {
            throw new ProviderException("SMS provider circuit breaker is open.", true, ex);
        }
    }

    /**
     * Returns the provider name reported by the underlying implementation.
     *
     * @return Returns the SMS provider name.
     */
    @Override
    public String providerName() {
        return delegate.providerName();
    }
}

