package huynv.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.notificationservice.service.channel.NotificationChannel;
import huynv.notificationservice.service.channel.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * Dispatches normalized notifications to enabled channels and persists notification history outcomes.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationChannelRegistry channelRegistry;
    private final NotificationHistoryWriter historyWriter;
    private final NotificationMetrics metrics;
    private final ObjectMapper objectMapper;

    /**
     * Creates a dispatcher that routes notification messages to channels and records outcomes.
     *
     * @param channelRegistry Registry used to locate channel strategies.
     * @param historyWriter Writer used to persist notification history records.
     * @param metrics Metrics reporter used to capture delivery outcomes.
     * @param objectMapper ObjectMapper used to serialize persisted audit payloads.
     * @return Initializes a notification dispatch service.
     */
    public NotificationDispatchService(NotificationChannelRegistry channelRegistry,
                                      NotificationHistoryWriter historyWriter,
                                      NotificationMetrics metrics,
                                      ObjectMapper objectMapper) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry");
        this.historyWriter = Objects.requireNonNull(historyWriter, "historyWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Dispatches a notification message through enabled delivery channels and persists history outcomes.
     *
     * @param message Normalized notification message to dispatch.
     * @return Performs side effects by sending notifications and persisting notification history records.
     */
    public void dispatch(NotificationMessage message) {
        Objects.requireNonNull(message, "message");

        for (NotificationChannelType channelType : preferredOrder()) {
            NotificationChannel channel = channelRegistry.channel(channelType);
            if (channel == null || !channel.isEnabled()) {
                continue;
            }
            dispatchSingle(channelType, channel, message);
        }
    }

    /**
     * Dispatches one notification message to a single channel and records the delivery outcome.
     *
     * @param channelType Channel type used for persisted history fields and metrics.
     * @param channel Channel strategy used to deliver the message.
     * @param message Notification message to deliver.
     * @return Performs side effects by delivering the message and persisting a history record.
     */
    private void dispatchSingle(NotificationChannelType channelType, NotificationChannel channel, NotificationMessage message) {
        try {
            NotificationStatus status = channel.send(message);
            historyWriter.write(
                    message.userId(),
                    message.tenantId(),
                    message.notificationType().name(),
                    channelType.name(),
                    toAuditPayload(message),
                    status
            );
            if (status == NotificationStatus.SENT) {
                metrics.markSent();
            }
        } catch (Exception ex) {
            historyWriter.write(
                    message.userId(),
                    message.tenantId(),
                    message.notificationType().name(),
                    channelType.name(),
                    toAuditPayload(message),
                    NotificationStatus.FAILED
            );
            metrics.markFailed();
            log.warn("Notification dispatch failed type={} channel={} tenantId={} userId={} error={}",
                    message.notificationType(),
                    channelType,
                    message.tenantId(),
                    message.userId(),
                    ex.getMessage());
            throw ex;
        }
    }

    /**
     * Builds a serialized audit payload that preserves the original event payload and template inputs.
     *
     * @param message Notification message containing the raw event and rendering inputs.
     * @return Returns a JSON string used for auditing and debugging.
     */
    private String toAuditPayload(NotificationMessage message) {
        try {
            Map<String, Object> payload = Map.of(
                    "eventPayload", message.rawEventPayload(),
                    "templateName", message.templateName(),
                    "templateModel", message.templateModel()
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"eventPayload\":\"" + safeJsonString(message.rawEventPayload()) + "\"}";
        }
    }

    /**
     * Escapes a string for safe embedding into a minimal JSON string value.
     *
     * @param value Raw input string to escape.
     * @return Returns a JSON-safe string with quotes and backslashes escaped.
     */
    private static String safeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Returns a deterministic channel processing order to reduce partial delivery duplication under retries.
     *
     * @return Returns the preferred channel order for dispatch.
     */
    private static EnumSet<NotificationChannelType> preferredOrder() {
        return EnumSet.of(NotificationChannelType.EMAIL, NotificationChannelType.SMS, NotificationChannelType.PUSH);
    }
}
