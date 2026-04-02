package huynv.eventinfra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configures isolated, bounded worker executors used to run external provider calls with backpressure protection.
 */
@Configuration
public class WorkerExecutorConfig {

    /**
     * Creates a bounded executor for email provider calls.
     *
     * @param properties Notification properties containing worker pool sizing.
     * @return Returns an ExecutorService used for email provider calls.
     */
    @Bean(name = "emailProviderExecutor")
    public ExecutorService emailProviderExecutor(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        NotificationProperties.Workers workers = properties.getWorkers();
        return newThreadPool("notification-email-provider", workers.getEmailPoolSize(), workers.getEmailQueueSize());
    }

    /**
     * Creates a bounded executor for SMS provider calls.
     *
     * @param properties Notification properties containing worker pool sizing.
     * @return Returns an ExecutorService used for SMS provider calls.
     */
    @Bean(name = "smsProviderExecutor")
    public ExecutorService smsProviderExecutor(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        NotificationProperties.Workers workers = properties.getWorkers();
        return newThreadPool("notification-sms-provider", workers.getSmsPoolSize(), workers.getSmsQueueSize());
    }

    /**
     * Creates a bounded executor for push provider calls.
     *
     * @param properties Notification properties containing worker pool sizing.
     * @return Returns an ExecutorService used for push provider calls.
     */
    @Bean(name = "pushProviderExecutor")
    public ExecutorService pushProviderExecutor(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        NotificationProperties.Workers workers = properties.getWorkers();
        return newThreadPool("notification-push-provider", workers.getPushPoolSize(), workers.getPushQueueSize());
    }

    /**
     * Creates a bounded ThreadPoolExecutor with a stable name prefix for thread identification.
     *
     * @param threadNamePrefix Prefix used for naming worker threads.
     * @param poolSize Fixed number of threads in the pool.
     * @param queueSize Maximum number of queued tasks allowed for backpressure.
     * @return Returns a bounded ExecutorService suitable for production isolation.
     */
    private static ExecutorService newThreadPool(String threadNamePrefix, int poolSize, int queueSize) {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
        ThreadFactory threadFactory = new NamedThreadFactory(threadNamePrefix);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                30L,
                TimeUnit.SECONDS,
                queue,
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}


