package huynv.eventinfra.resilience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Declares bounded executors used by Resilience4j time limiters for synchronous HTTP calls.
 */
@Configuration
public class ResilienceExecutorsConfig {

    /**
     * Creates a bounded executor used to run blocking dependency calls under a time limiter.
     *
     * @return Returns an executor service used for time-limited call execution.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService resilienceCallExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Creates a scheduler used by Resilience4j time limiters to enforce timeouts.
     *
     * @return Returns a scheduled executor service used for timeout scheduling.
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService resilienceTimeoutScheduler() {
        return Executors.newScheduledThreadPool(2);
    }
}


