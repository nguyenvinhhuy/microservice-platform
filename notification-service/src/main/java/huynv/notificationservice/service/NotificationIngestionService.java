package huynv.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import huynv.event.BaseEvent;
import huynv.eventinfra.dispatcher.NotificationJob;
import huynv.eventinfra.dispatcher.NotificationJobPublisher;
import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationPriority;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.eventinfra.exception.RetryableDependencyException;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.notificationservice.service.contact.UserContact;
import huynv.notificationservice.service.contact.UserContactResolver;
import huynv.notificationservice.service.recipient.RecipientResolutionService;
import huynv.eventinfra.util.MdcUtil;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Expands upstream events into per-channel notification jobs and publishes them to priority topics.
 */
@Service
public class NotificationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIngestionService.class);

    private final NotificationProcessingService processingService;
    private final RecipientResolutionService recipientResolutionService;
    private final UserContactResolver userContactResolver;
    private final NotificationPreferenceService preferenceService;
    private final NotificationJobPublisher jobPublisher;
    private final NotificationProperties properties;
    private final NotificationHistoryWriter historyWriter;
    private final NotificationMetrics metrics;
    private final Tracer tracer;
    private final TemplatePathResolver templatePathResolver;

    /**
     * Creates an ingestion service that resolves recipients, applies preferences, and publishes channel jobs.
     *
     * @param processingService Processing service used to parse envelopes and map intents.
     * @param recipientResolutionService Recipient resolver used to map orderId to userId when needed.
     * @param userContactResolver Contact resolver used to resolve delivery targets for the user.
     * @param preferenceService Preference service used to filter enabled channels.
     * @param jobPublisher Publisher used to publish priority jobs to Kafka.
     * @param properties Notification properties used to control dispatcher behavior.
     * @param historyWriter History writer used to persist skipped delivery outcomes when jobs cannot be created.
     * @param metrics Metrics used to record skipped outcomes for contract drift detection.
     * @param tracer Tracer used to create resolve and dispatch spans.
     * @param templatePathResolver Template resolver used to apply tenant overrides and enforce canonical paths.
     * @return Initializes a notification ingestion service.
     */
    public NotificationIngestionService(NotificationProcessingService processingService,
                                        RecipientResolutionService recipientResolutionService,
                                        UserContactResolver userContactResolver,
                                        NotificationPreferenceService preferenceService,
                                        NotificationJobPublisher jobPublisher,
                                        NotificationProperties properties,
                                        NotificationHistoryWriter historyWriter,
                                        NotificationMetrics metrics,
                                        Tracer tracer,
                                        TemplatePathResolver templatePathResolver) {
        this.processingService = Objects.requireNonNull(processingService, "processingService");
        this.recipientResolutionService = Objects.requireNonNull(recipientResolutionService, "recipientResolutionService");
        this.userContactResolver = Objects.requireNonNull(userContactResolver, "userContactResolver");
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService");
        this.jobPublisher = Objects.requireNonNull(jobPublisher, "jobPublisher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.historyWriter = Objects.requireNonNull(historyWriter, "historyWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.templatePathResolver = Objects.requireNonNull(templatePathResolver, "templatePathResolver");
    }

    /**
     * Ingests an upstream event payload and publishes per-channel jobs for downstream dispatch and sending.
     *
     * @param rawJson Raw upstream BaseEvent JSON payload.
     * @return Performs side effects by publishing per-channel jobs to Kafka.
     */
    public void ingest(String rawJson) {
        Objects.requireNonNull(rawJson, "rawJson");

        Span ingestionSpan = tracer.spanBuilder().name("notification.ingestion").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(ingestionSpan)) {
            if (!properties.getDispatcher().isEnabled()) {
                processingService.processRawEvent(rawJson);
                return;
            }

            BaseEvent<JsonNode> envelope = processingService.parseEnvelope(rawJson);
            NotificationIntent intent = processingService.toIntent(envelope, rawJson);
            if (intent == null) {
                return;
            }

            Optional<Long> resolvedUserId;
            Span resolveSpan = tracer.spanBuilder().name("notification.resolve-recipient").start();
            try (Tracer.SpanInScope resolveScope = tracer.withSpan(resolveSpan)) {
                resolvedUserId = recipientResolutionService.resolveUserId(intent.tenantId(), intent.userId(), intent.orderId());
            } catch (Exception ex) {
                resolveSpan.error(ex);
                metrics.incrementRecipientResolutionFailure();
                throw ex;
            } finally {
                resolveSpan.end();
            }

            if (resolvedUserId.isEmpty()) {
                metrics.incrementRecipientResolutionFailure();
                throw new RetryableDependencyException("Recipient could not be resolved eventId=" + intent.eventId()
                        + " eventType=" + intent.eventType()
                        + " tenantId=" + intent.tenantId()
                        + " orderId=" + intent.orderId() + ".");
            }

            Long userId = resolvedUserId.get();
            UserContact contact = userContactResolver.resolve(intent.tenantId(), userId).orElse(null);
            EnumSet<NotificationChannelType> enabledChannels = preferenceService.enabledChannels(intent.tenantId(), userId);
            NotificationPriority priority = toPriority(intent);

            Span dispatchSpan = tracer.spanBuilder().name("notification.dispatch").start();
            try (Tracer.SpanInScope dispatchScope = tracer.withSpan(dispatchSpan)) {
                for (NotificationChannelType channel : enabledChannels) {
                    NotificationJob job = toJob(intent, userId, contact, channel, priority);
                    if (job == null) {
                        metrics.markSkipped();
                        String notificationId = historyWriter.writeWithMetadata(
                                userId,
                                intent.tenantId(),
                                intent.eventId(),
                                intent.notificationType().name(),
                                channel.name(),
                                priority.name(),
                                "RESOLUTION",
                                intent.rawEventPayload(),
                                NotificationStatus.SKIPPED
                        ).getId().toString();
                        MdcUtil.putAll(Map.of(
                                "notificationId", notificationId,
                                "channel", channel.name(),
                                "priority", priority.name(),
                                "provider", "RESOLUTION"
                        ));
                        log.info("Notification skipped due to missing contact eventId={} tenantId={} userId={} channel={}",
                                intent.eventId(),
                                intent.tenantId(),
                                userId,
                                channel);
                        continue;
                    }
                    jobPublisher.publish(job);
                }
            } catch (Exception ex) {
                dispatchSpan.error(ex);
                throw ex;
            } finally {
                dispatchSpan.end();
            }
        } catch (Exception ex) {
            ingestionSpan.error(ex);
            throw ex;
        } finally {
            ingestionSpan.end();
        }
    }

    /**
     * Converts a normalized intent into a per-channel delivery job.
     *
     * @param intent Normalized notification intent.
     * @param userId Recipient user identifier.
     * @param contact Contact information resolved for the user.
     * @param channel Channel to create the job for.
     * @param priority Priority assigned to the job.
     * @return Returns a job or null when the channel cannot be satisfied by resolved contact data.
     */
    private NotificationJob toJob(NotificationIntent intent,
                                 Long userId,
                                 UserContact contact,
                                 NotificationChannelType channel,
                                 NotificationPriority priority) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(priority, "priority");

        String email = contact == null ? null : contact.email();
        String phone = contact == null ? null : contact.phoneNumber();
        List<String> pushTokens = contact == null || contact.pushTokens() == null ? List.of() : contact.pushTokens();

        if (channel == NotificationChannelType.EMAIL && (email == null || email.isBlank())) {
            return null;
        }
        if (channel == NotificationChannelType.SMS && (phone == null || phone.isBlank())) {
            return null;
        }
        if (channel == NotificationChannelType.PUSH && (pushTokens == null || pushTokens.isEmpty())) {
            return null;
        }

        return new NotificationJob(
                intent.eventId(),
                intent.eventType(),
                intent.tenantId(),
                userId,
                intent.orderId() == null ? null : intent.orderId().toString(),
                intent.notificationType().name(),
                channel.name(),
                priority.name(),
                intent.subject(),
                templatePath(intent.tenantId(), intent.templateName()),
                intent.templateModel(),
                intent.rawEventPayload(),
                email,
                phone,
                pushTokens,
                intent.traceId(),
                intent.correlationId()
        );
    }

    /**
     * Resolves the template resource path using tenant override and default conventions.
     *
     * @param tenantId Tenant identifier used to resolve tenant override templates when present.
     * @param baseTemplateName Base template name produced by intent mapping.
     * @return Returns the resolved template path for Thymeleaf rendering.
     */
    private String templatePath(Long tenantId, String baseTemplateName) {
        Objects.requireNonNull(baseTemplateName, "baseTemplateName");
        return templatePathResolver.resolveForTenant(tenantId, baseTemplateName);
    }

    /**
     * Maps notification types to a priority level used for dispatch topic selection.
     *
     * @param intent Notification intent used to determine priority.
     * @return Returns the resolved priority for the intent.
     */
    private static NotificationPriority toPriority(NotificationIntent intent) {
        return switch (intent.notificationType()) {
            case PAYMENT_FAILED -> NotificationPriority.HIGH;
            case PAYMENT_SUCCEEDED -> NotificationPriority.NORMAL;
            case ORDER_CANCELLED -> NotificationPriority.HIGH;
            case ORDER_CREATED -> NotificationPriority.NORMAL;
        };
    }
}




