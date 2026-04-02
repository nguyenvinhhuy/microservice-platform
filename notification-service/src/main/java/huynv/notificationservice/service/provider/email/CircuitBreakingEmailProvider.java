package huynv.notificationservice.service.provider.email;

import huynv.notificationservice.service.provider.ProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Wraps an EmailProvider with a Resilience4j circuit breaker for external dependency protection.
 */
@Component
@Primary
public class CircuitBreakingEmailProvider implements EmailProvider {

    private final EmailProvider delegate;
    private final CircuitBreaker circuitBreaker;

    /**
     * Creates a circuit-breaking wrapper around the configured email provider implementation.
     *
     * @param delegate Underlying email provider implementation.
     * @param circuitBreakerRegistry Circuit breaker registry used to obtain provider circuit breaker instances.
     * @return Initializes a circuit-breaking email provider wrapper.
     */
    public CircuitBreakingEmailProvider(
            @Qualifier("smtpEmailProvider") EmailProvider delegate,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("emailProvider");
    }

    /**
     * Sends an email request through the delegate provider while rejecting calls when the circuit breaker is open.
     *
     * @param request Request to send through the underlying provider.
     * @return Performs side effects by delivering an email when allowed by the circuit breaker.
     */
    @Override
    public void send(EmailSendRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            circuitBreaker.executeRunnable(() -> delegate.send(request));
        } catch (CallNotPermittedException ex) {
            throw new ProviderException("Email provider circuit breaker is open.", true, ex);
        }
    }

    /**
     * Returns the provider name reported by the underlying implementation.
     *
     * @return Returns the email provider name.
     */
    @Override
    public String providerName() {
        return delegate.providerName();
    }
}

