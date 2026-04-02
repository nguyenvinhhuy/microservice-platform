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
import huynv.notificationservice.service.provider.sms.SmsProvider;
import huynv.notificationservice.service.provider.sms.SmsSendRequest;
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
 * Executes SMS delivery jobs using provider integrations with per-channel idempotency and rate limiting.
 */
@Component
@ConditionalOnExpression("${notification.dispatcher.enabled:false} && ${notification.dispatch.enabled:true} && ${notification.channels.sms-enabled:false} && ${notification.sms.enabled:true}")
public class SmsWorker {

    private static final Logger log = LoggerFactory.getLogger(SmsWorker.class);

    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiterService;
    private final ProcessedNotificationRepository processedNotificationRepository;
    private final TemplateRenderingService templateRenderingService;
    private final SmsProvider smsProvider;
    private final ProviderProperties providerProperties;
    private final ExecutorService providerExecutor;
    private final NotificationHistoryWriter historyWriter;
    private final NotificationMetrics metrics;
    private final Tracer tracer;

    /**
     * Creates an SMS worker for executing SMS delivery jobs.
     *
     * @param objectMapper ObjectMapper used to deserialize job payloads.
     * @param rateLimiterService Rate limiter used to protect external providers.
     * @param processedNotificationRepository Repository used for per-channel idempotency markers.
     * @param templateRenderingService Template renderer used to render message bodies.
     * @param smsProvider SMS provider used to send messages.
     * @param providerProperties Provider properties used to enforce strict provider timeouts.
     * @param providerExecutor Isolated executor used to run SMS provider calls with bounded queueing.
     * @param historyWriter History writer used to persist delivery outcomes.
     * @param metrics Metrics used to record rate limiting and provider latency.
     * @param tracer Tracer used to create delivery spans.
     * @return Initializes an SMS worker.
     */
    public SmsWorker(ObjectMapper objectMapper,
                     RateLimiterService rateLimiterService,
                     ProcessedNotificationRepository processedNotificationRepository,
                     TemplateRenderingService templateRenderingService,
                     SmsProvider smsProvider,
                     ProviderProperties providerProperties,
                     @Qualifier("smsProviderExecutor") ExecutorService providerExecutor,
                     NotificationHistoryWriter historyWriter,
                     NotificationMetrics metrics,
                     Tracer tracer) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.rateLimiterService = Objects.requireNonNull(rateLimiterService, "rateLimiterService");
        this.processedNotificationRepository = Objects.requireNonNull(processedNotificationRepository, "processedNotificationRepository");
        this.templateRenderingService = Objects.requireNonNull(templateRenderingService, "templateRenderingService");
        this.smsProvider = Objects.requireNonNull(smsProvider, "smsProvider");
        this.providerProperties = Objects.requireNonNull(providerProperties, "providerProperties");
        this.providerExecutor = Objects.requireNonNull(providerExecutor, "providerExecutor");
        this.historyWriter = Objects.requireNonNull(historyWriter, "historyWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    /**
     * Consumes SMS delivery jobs and attempts to send them through the configured provider.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by sending SMS messages and persisting idempotency markers.
     */
    @KafkaListener(
            id = "notification-sms-worker",
            topics = "${notification.dispatcher.sms-topic:notification.sms}",
            groupId = "${notification.dispatcher.sms-worker-group-id:notification-sms-worker}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSmsJob(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(acknowledgment, "acknowledgment");

        NotificationJob job = objectMapper.readValue(record.value(), NotificationJob.class);
        String providerName = smsProvider.providerName();
        String priority = job.priority();
        long dispatchStartNanos = System.nanoTime();
        putMdc(job, providerName);
        String idempotencyEventId = IdempotencyKeyUtil.tenantScopedEventId(job.tenantId(), job.eventId());

        Span span = TracingUtil.startSpan(tracer, "notification.worker.sms", job.traceId());
        span.tag("channel", NotificationChannelType.SMS.name());
        if (job.priority() != null) {
            span.tag("priority", job.priority());
        }
        Tracer.SpanInScope scope = tracer.withSpan(span);
        try {
            if (!rateLimiterService.tryAcquire(job.tenantId(), NotificationChannelType.SMS)) {
                metrics.incrementRateLimited(NotificationChannelType.SMS);
                metrics.incrementRateLimitRejectedTotal(NotificationChannelType.SMS, providerName, job.tenantId(), priority);
                metrics.incrementRetryTotal(NotificationChannelType.SMS, providerName, job.tenantId(), priority);
                MdcUtil.putAll(Map.of("delivery_status", "RATE_LIMITED"));
                log.info("SMS rate limited eventId={} tenantId={}", job.eventId(), job.tenantId());
                throw new RetryableDependencyException("SMS delivery rate limited tenantId=" + job.tenantId() + ".");
            }

            if (processedNotificationRepository.existsByIdEventIdAndIdChannel(idempotencyEventId, NotificationChannelType.SMS)) {
                acknowledgment.acknowledge();
                return;
            }

            String body = templateRenderingService.render(job.templateName(), job.templateModel());
            long providerStartNanos = System.nanoTime();
            try {
                sendWithTimeout(job, providerName, priority, () ->
                        smsProvider.send(new SmsSendRequest(job.recipientPhoneNumber(), body))
                );
                metrics.markSent();
                metrics.markSent(NotificationChannelType.SMS, providerName, job.tenantId(), priority);
                metrics.recordChannelLatency(NotificationChannelType.SMS, providerName, job.tenantId(), priority, NotificationStatus.SENT.name(), providerStartNanos);
                processedNotificationRepository.save(huynv.notificationservice.domain.ProcessedNotification.create(idempotencyEventId, NotificationChannelType.SMS));
                String notificationId = historyWriter.writeWithMetadata(
                        job.userId(),
                        job.tenantId(),
                        job.eventId(),
                        job.notificationType(),
                        NotificationChannelType.SMS.name(),
                        job.priority(),
                        providerName,
                        record.value(),
                        NotificationStatus.SENT
                ).getId().toString();
                MdcUtil.putAll(Map.of("notificationId", notificationId));
                MdcUtil.putAll(Map.of("delivery_status", NotificationStatus.SENT.name()));
                log.info("SMS delivered eventId={} tenantId={} userId={} provider={}", job.eventId(), job.tenantId(), job.userId(), providerName);
                acknowledgment.acknowledge();
            } catch (ProviderException ex) {
                metrics.markFailed();
                metrics.markFailed(NotificationChannelType.SMS, providerName, job.tenantId(), priority);
                metrics.recordChannelLatency(NotificationChannelType.SMS, providerName, job.tenantId(), priority, NotificationStatus.FAILED.name(), providerStartNanos);
                String notificationId = historyWriter.writeWithMetadata(
                        job.userId(),
                        job.tenantId(),
                        job.eventId(),
                        job.notificationType(),
                        NotificationChannelType.SMS.name(),
                        job.priority(),
                        providerName,
                        record.value(),
                        NotificationStatus.FAILED
                ).getId().toString();
                MdcUtil.putAll(Map.of("notificationId", notificationId));
                MdcUtil.putAll(Map.of("delivery_status", NotificationStatus.FAILED.name()));
                log.warn("SMS delivery failed eventId={} tenantId={} userId={} provider={} retryable={} message={}",
                        job.eventId(),
                        job.tenantId(),
                        job.userId(),
                        providerName,
                        ex.isRetryable(),
                        ex.getMessage());
                if (ex.isRetryable()) {
                    metrics.incrementRetryTotal(NotificationChannelType.SMS, providerName, job.tenantId(), priority);
                    span.error(ex);
                    throw ex;
                }
                processedNotificationRepository.save(huynv.notificationservice.domain.ProcessedNotification.create(idempotencyEventId, NotificationChannelType.SMS));
                acknowledgment.acknowledge();
            }
        } finally {
            metrics.recordDispatchLatency(NotificationChannelType.SMS, providerName, job.tenantId(), priority, dispatchStartNanos);
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
        Duration timeout = providerProperties.getSms().getTimeout();

        Future<?> future;
        try {
            future = providerExecutor.submit(action);
        } catch (RejectedExecutionException ex) {
            metrics.incrementWorkerQueueRejectedTotal(NotificationChannelType.SMS, provider, job.tenantId(), priority);
            throw new RetryableDependencyException("SMS worker queue is full tenantId=" + job.tenantId() + ".", ex);
        }

        try {
            future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            metrics.incrementProviderTimeoutTotal(NotificationChannelType.SMS, provider, job.tenantId(), priority);
            throw new ProviderException("SMS provider timed out after " + timeout + ".", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException("SMS provider call interrupted.", true, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ProviderException providerException) {
                throw providerException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ProviderException("SMS provider call failed.", true, cause);
        }
    }

    /**
     * Populates MDC values used by structured logging for SMS delivery jobs.
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
        values.put("channel", NotificationChannelType.SMS.name());
        values.put("priority", job.priority());
        values.put("provider", provider);
        values.put("traceId", job.traceId());
        values.put("correlationId", job.correlationId());
        MdcUtil.putAll(values);
    }
}

