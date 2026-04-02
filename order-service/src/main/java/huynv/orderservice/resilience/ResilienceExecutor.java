package huynv.orderservice.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import huynv.orderservice.exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Executes blocking downstream calls with CircuitBreaker, Retry, Timeout, and Bulkhead protections.
 */
@Component
public class ResilienceExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ExecutorService callExecutor;

    /**
     * Creates an executor that applies Resilience4j protections around caller-provided blocking logic.
     *
     * @param circuitBreakerRegistry Registry used to resolve circuit breaker instances.
     * @param retryRegistry Registry used to resolve retry instances.
     * @param timeLimiterRegistry Registry used to resolve timeout instances.
     * @param bulkheadRegistry Registry used to resolve bulkhead instances.
     * @param callExecutor Executor used to run blocking calls under a time limiter.
     * @return Initializes a resilience executor instance.
     */
    public ResilienceExecutor(CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry,
                              TimeLimiterRegistry timeLimiterRegistry,
                              BulkheadRegistry bulkheadRegistry,
                              ExecutorService callExecutor) {
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.retryRegistry = Objects.requireNonNull(retryRegistry, "retryRegistry");
        this.timeLimiterRegistry = Objects.requireNonNull(timeLimiterRegistry, "timeLimiterRegistry");
        this.bulkheadRegistry = Objects.requireNonNull(bulkheadRegistry, "bulkheadRegistry");
        this.callExecutor = Objects.requireNonNull(callExecutor, "callExecutor");
    }

    /**
     * Executes a caller-supplied blocking function under all configured resilience protections.
     *
     * @param instanceName Resilience4j instance name used to resolve configs across modules.
     * @param supplier Blocking supplier that performs the downstream call and returns a value.
     * @param <T> Return type of the downstream call.
     * @return Returns the supplier result or throws the decorated exception on failure.
     */
    public <T> T execute(String instanceName, Supplier<T> supplier) {
        Objects.requireNonNull(instanceName, "instanceName");
        Objects.requireNonNull(supplier, "supplier");

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);

        Supplier<T> protectedSupplier = () -> {
            Supplier<CompletableFuture<T>> futureSupplier = () -> CompletableFuture.supplyAsync(supplier, callExecutor);
            java.time.Duration timeout = timeLimiter.getTimeLimiterConfig().getTimeoutDuration();
            CompletableFuture<T> future = futureSupplier.get()
                    .orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            try {
                return future.join();
            } catch (RuntimeException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof TimeoutException) {
                    throw new DownstreamServiceUnavailableException("Downstream call timed out instanceName=" + instanceName + ".", cause);
                }
                throw ex;
            }
        };

        Supplier<T> bulkheaded = Bulkhead.decorateSupplier(bulkhead, protectedSupplier);
        Supplier<T> circuitProtected = CircuitBreaker.decorateSupplier(circuitBreaker, bulkheaded);
        Supplier<T> retried = Retry.decorateSupplier(retry, circuitProtected);
        try {
            return retried.get();
        } catch (CallNotPermittedException | BulkheadFullException ex) {
            throw new DownstreamServiceUnavailableException("Downstream call rejected by protection instanceName=" + instanceName + ".", ex);
        } catch (DownstreamServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }
}
