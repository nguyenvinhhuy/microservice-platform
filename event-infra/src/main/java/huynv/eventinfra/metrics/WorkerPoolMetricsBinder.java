package huynv.eventinfra.metrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Registers worker pool gauges for queue depth and active thread counts to support production backpressure observability.
 */
@Component
public class WorkerPoolMetricsBinder {

    /**
     * Creates a binder that registers gauges for each channel worker pool.
     *
     * @param meterRegistry Meter registry used to register gauges.
     * @param emailProviderExecutor Executor used for email provider calls.
     * @param smsProviderExecutor Executor used for SMS provider calls.
     * @param pushProviderExecutor Executor used for push provider calls.
     * @return Registers gauge meters for dispatch queue depth and worker utilization.
     */
    public WorkerPoolMetricsBinder(MeterRegistry meterRegistry,
                                   @Qualifier("emailProviderExecutor") ExecutorService emailProviderExecutor,
                                   @Qualifier("smsProviderExecutor") ExecutorService smsProviderExecutor,
                                   @Qualifier("pushProviderExecutor") ExecutorService pushProviderExecutor) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        register(meterRegistry, "EMAIL", emailProviderExecutor);
        register(meterRegistry, "SMS", smsProviderExecutor);
        register(meterRegistry, "PUSH", pushProviderExecutor);
    }

    /**
     * Registers queue size and active thread gauges for an executor using fixed label dimensions.
     *
     * @param meterRegistry Meter registry used to register gauges.
     * @param channel Channel label associated with the executor.
     * @param executor Executor instance used for channel provider calls.
     * @return Performs side effects by registering gauge meters.
     */
    private static void register(MeterRegistry meterRegistry, String channel, ExecutorService executor) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(executor, "executor");
        if (!(executor instanceof ThreadPoolExecutor threadPoolExecutor)) {
            return;
        }

        Gauge.builder("notification_dispatch_queue_size", threadPoolExecutor, pool -> pool.getQueue().size())
                .description("Current queued task count for notification worker executors.")
                .tag("tenantId", "unknown")
                .tag("channel", channel)
                .tag("provider", "worker")
                .tag("priority", "unknown")
                .register(meterRegistry);

        Gauge.builder("notification_worker_active_threads", threadPoolExecutor, ThreadPoolExecutor::getActiveCount)
                .description("Current active thread count for notification worker executors.")
                .tag("tenantId", "unknown")
                .tag("channel", channel)
                .tag("provider", "worker")
                .tag("priority", "unknown")
                .register(meterRegistry);
    }
}


