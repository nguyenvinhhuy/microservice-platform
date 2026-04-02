package huynv.notificationservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.eventinfra.config.ProviderProperties;
import huynv.eventinfra.dispatcher.NotificationJob;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.notificationservice.repository.ProcessedNotificationRepository;
import huynv.notificationservice.service.NotificationHistoryWriter;
import huynv.notificationservice.service.TemplateRenderingService;
import huynv.notificationservice.service.provider.ProviderException;
import huynv.notificationservice.service.provider.push.PushProvider;
import huynv.notificationservice.service.provider.push.PushSendRequest;
import huynv.notificationservice.service.ratelimit.RateLimiterService;
import huynv.notificationservice.util.IdempotencyKeyUtil;
import huynv.eventinfra.util.MdcUtil;
import huynv.eventinfra.util.TracingUtil;
import huynv.notificationservice.exception.RetryableDependencyException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes push delivery jobs using provider integrations with per-channel idempotency and rate limiting.
 */
@Component
@ConditionalOnExpression("${notification.dispatcher.enabled:false} && ${notification.dispatch.enabled:true} && ${notification.channels.push-enabled:false} && ${notification.push.enabled:true}")
public class PushWorker {

    private static final Logger log = LoggerFactory.getLogger(PushWorker.class);

    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiterService;
    private final ProcessedNotificationRepository processedNotificationRepository;
    private final TemplateRenderingService templateRenderingService;
    private final PushProvider pushProvider;
    private final ProviderProperties providerProperties;
    private final ExecutorService providerExecutor;
    private final NotificationHistoryWriter historyWriter;
    private final NotificationMetrics metrics;
    private final Tracer tracer;

    /**
     * Creates a push worker for executing push delivery jobs.
     *
     * @param objectMapper ObjectMapper used to deserialize job payloads.
     * @param rateLimiterService Rate limiter used to protect external providers.
     * @param processedNotificationRepository Repository used for per-channel idempotency markers.
     * @param templateRenderingService Template renderer used to render message bodies.
     * @param pushProvider Push provider used to send notifications.
     * @param providerProperties Provider properties used to enforce strict provider timeouts.
     * @param providerExecutor Isolated executor used to run push provider calls with bounded queueing.
     * @param historyWriter History writer used to persist delivery outcomes.
     * @param metrics Metrics used to record rate limiting and provider latency.
     * @param tracer Tracer used to create delivery spans.
     * @return Initializes a push worker.
     */
    public PushWorker(ObjectMapper objectMapper,
                      RateLimiterService rateLimiterService,
                      ProcessedNotificationRepository processedNotificationRepository,
                      TemplateRenderingService templateRenderingService,
                      PushProvider pushProvider,
                      ProviderProperties providerProperties,
                      @Qualifier("pushProviderExecutor") ExecutorService providerExecutor,
                      NotificationHistoryWriter historyWriter,
                      NotificationMetrics metrics,
                      Tracer tracer) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.rateLimiterService = Objects.requireNonNull(rateLimiterService, "rateLimiterService");
        this.processedNotificationRepository = Objects.requireNonNull(processedNotificationRepository, "processedNotificationRepository");
        this.templateRenderingService = Objects.requireNonNull(templateRenderingService, "templateRenderingService");
        this.pushProvider = Objects.requireNonNull(pushProvider, "pushProvider");
        this.providerProperties = Objects.requireNonNull(providerProperties, "providerProperties");
        this.providerExecutor = Objects.requireNonNull(providerExecutor, "providerExecutor");
        this.historyWriter = Objects.requireNonNull(historyWriter, "historyWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    /**
     * Consumes push delivery jobs and attempts to send them through the configured provider.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by sending push notifications and persisting idempotency markers.
     */
    @KafkaListener(
            id = "notification-push-worker",
            topics = "${notification.dispatcher.push-topic:notification.push}",
            groupId = "${notification.dispatcher.push-worker-group-id:notification-push-worker}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPushJob(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(acknowledgment, "acknowledgment");

        NotificationJob job = objectMapper.readValue(record.value(), NotificationJob.class);
        String providerName = pushProvider.providerName();
        String priority = job.priority();
        long dispatchStartNanos = System.nanoTime();
        putMdc(job, providerName);
        String idempotencyEventId = IdempotencyKeyUtil.tenantScopedEventId(job.tenantId(), job.eventId());

        Span span = TracingUtil.startSpan(tracer, "notification.worker.push", job.traceId());
        span.tag("channel", NotificationChannelType.PUSH.name());
        if (job.priority() != null) {
            span.tag("priority", job.priority());
        }
        Tracer.SpanInScope scope = tracer.withSpan(span);

        try {
            if (!rateLimiterService.tryAcquire(job.tenantId(), NotificationChannelType.PUSH)) {
                metrics.incrementRateLimited(NotificationChannelType.PUSH);
                metrics.incrementRateLimitRejectedTotal(NotificationChannelType.PUSH, providerName, job.tenantId(), priority);
                metrics.incrementRetryTotal(NotificationChannelType.PUSH, providerName, job.tenantId(), priority);
                MdcUtil.putAll(Map.of("delivery_status", "RATE_LIMITED"));
                log.info("Push rate limited eventId={} tenantId={}", job.eventId(), job.tenantId());
                throw new RetryableDependencyException("Push delivery rate limited tenantId=" + job.tenantId() + ".");
            }

            if (processedNotificationRepository.existsByIdEventIdAndIdChannel(idempotencyEventId, NotificationChannelType.PUSH)) {
                acknowledgment.acknowledge();
                return;
            }

            String body = templateRenderingService.render(job.templateName(), job.templateModel());
            long providerStartNanos = System.nanoTime();
            try {
                sendWithTimeout(job, providerName, priority, () ->
                        pushProvider.send(new PushSendRequest(job.recipientPushTokens(), body))
                );
                metrics.markSent();
                metrics.markSent(NotificationChannelType.PUSH, providerName, job.tenantId(), priority);
                metrics.recordChannelLatency(NotificationChannelType.PUSH, providerName, job.tenantId(), priority, NotificationStatus.SENT.name(), providerStartNanos);
                processedNotificationRepository.save(huynv.notificationservice.domain.ProcessedNotification.create(idempotencyEventId, NotificationChannelType.PUSH));
                String notificationId = historyWriter.writeWithMetadata(
                        job.userId(),
                        job.tenantId(),
                        job.eventId(),
                        job.notificationType(),
                        NotificationChannelType.PUSH.name(),
                        job.priority(),
                        providerName,
                        record.value(),
                        NotificationStatus.SENT
                ).getId().toString();
                MdcUtil.putAll(Map.of("notificationId", notificationId));
                MdcUtil.putAll(Map.of("delivery_status", NotificationStatus.SENT.name()));
                log.info("Push delivered eventId={} tenantId={} userId={} provider={}", job.eventId(), job.tenantId(), job.userId(), providerName);
                acknowledgment.acknowledge();
            } catch (ProviderException ex) {
                metrics.markFailed();
                metrics.markFailed(NotificationChannelType.PUSH, providerName, job.tenantId(), priority);
                metrics.recordChannelLatency(NotificationChannelType.PUSH, providerName, job.tenantId(), priority, NotificationStatus.FAILED.name(), providerStartNanos);
                String notificationId = historyWriter.writeWithMetadata(
                        job.userId(),
                        job.tenantId(),
                        job.eventId(),
                        job.notificationType(),
                        NotificationChannelType.PUSH.name(),
                        job.priority(),
                        providerName,
                        record.value(),
                        NotificationStatus.FAILED
                ).getId().toString();
                MdcUtil.putAll(Map.of("notificationId", notificationId));
                MdcUtil.putAll(Map.of("delivery_status", NotificationStatus.FAILED.name()));
                log.warn("Push delivery failed eventId={} tenantId={} userId={} provider={} retryable={} message={}",
                        job.eventId(),
                        job.tenantId(),
                        job.userId(),
                        providerName,
                        ex.isRetryable(),
                        ex.getMessage());
                if (ex.isRetryable()) {
                    metrics.incrementRetryTotal(NotificationChannelType.PUSH, providerName, job.tenantId(), priority);
                    span.error(ex);
                    throw ex;
                }
                processedNotificationRepository.save(huynv.notificationservice.domain.ProcessedNotification.create(idempotencyEventId, NotificationChannelType.PUSH));
                acknowledgment.acknowledge();
            }
        } finally {
            metrics.recordDispatchLatency(NotificationChannelType.PUSH, providerName, job.tenantId(), priority, dispatchStartNanos);
            scope.close();
            span.end();
            MdcUtil.clear();
        }
    }

    /**
     * Executes a provider call on an isolated executor and enforces a strict timeout for worker safety.
     *
     * @param job Notification job used for metric and correlation labels.
     * @param provider Provider name used for delivery.
     * @param priority Priority label used for dispatch observability.
     * @param action Provider call to execute.
     * @return Performs side effects by executing the provider call or throwing on timeout or overload.
     */
    private void sendWithTimeout(NotificationJob job, String provider, String priority, Runnable action) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(action, "action");
        Duration timeout = providerProperties.getPush().getTimeout();

        Future<?> future;
        try {
            future = providerExecutor.submit(action);
        } catch (RejectedExecutionException ex) {
            metrics.incrementWorkerQueueRejectedTotal(NotificationChannelType.PUSH, provider, job.tenantId(), priority);
            throw new RetryableDependencyException("Push worker queue is full tenantId=" + job.tenantId() + ".", ex);
        }

        try {
            future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            metrics.incrementProviderTimeoutTotal(NotificationChannelType.PUSH, provider, job.tenantId(), priority);
            throw new ProviderException("Push provider timed out after " + timeout + ".", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Push provider call interrupted.", true, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ProviderException providerException) {
                throw providerException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ProviderException("Push provider call failed.", true, cause);
        }
    }

    /**
     * Populates MDC values used by structured logging for push delivery jobs.
     *
     * @param job Notification job being processed.
     * @param provider Provider name used for delivery.
     * @return Performs a side effect by populating MDC keys for the current thread.
     */
    private static void putMdc(NotificationJob job, String provider) {
        Map<String, String> values = new HashMap<>();
        values.put("eventId", job.eventId());
        values.put("tenantId", job.tenantId() == null ? null : job.tenantId().toString());
        values.put("userId", job.userId() == null ? null : job.userId().toString());
        values.put("orderId", job.orderId());
        values.put("channel", "PUSH");
        values.put("priority", job.priority());
        values.put("provider", provider);
        values.put("traceId", job.traceId());
        values.put("correlationId", job.correlationId());
        MdcUtil.putAll(values);
    }
}

