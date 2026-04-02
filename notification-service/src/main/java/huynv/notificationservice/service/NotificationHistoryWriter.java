package huynv.notificationservice.service;

import huynv.notificationservice.domain.NotificationHistory;
import huynv.notificationservice.domain.NotificationStatus;
import huynv.notificationservice.repository.NotificationHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Persists notification history records using isolated transactions so retry workflows do not lose audit data.
 */
@Service
public class NotificationHistoryWriter {

    private final NotificationHistoryRepository historyRepository;

    /**
     * Creates a writer that persists notification history records.
     *
     * @param historyRepository Repository used to persist history rows.
     * @return Initializes a notification history writer.
     */
    public NotificationHistoryWriter(NotificationHistoryRepository historyRepository) {
        this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository");
    }

    /**
     * Persists a notification history record in a new transaction.
     *
     * @param userId User identifier for the intended recipient when available.
     * @param tenantId Tenant identifier used for data isolation.
     * @param type Notification type describing the business intent.
     * @param channel Delivery channel used for this attempt.
     * @param payload Serialized payload persisted for auditing and debugging.
     * @param status Delivery status for this attempt.
     * @return Persists a notification history record and returns the saved entity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationHistory write(Long userId,
                                     Long tenantId,
                                     String type,
                                     String channel,
                                     String payload,
                                     NotificationStatus status) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        return historyRepository.save(NotificationHistory.create(userId, tenantId, type, channel, payload, status));
    }

    /**
     * Persists a notification history record with correlation metadata in a new transaction.
     *
     * @param userId User identifier for the intended recipient when available.
     * @param tenantId Tenant identifier used for data isolation.
     * @param eventId Event identifier used for end-to-end correlation.
     * @param type Notification type describing the business intent.
     * @param channel Delivery channel used for this attempt.
     * @param priority Priority value used for scheduling and operational reasoning.
     * @param provider Provider name used for external delivery.
     * @param payload Serialized payload persisted for auditing and debugging.
     * @param status Delivery status for this attempt.
     * @return Persists a notification history record and returns the saved entity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationHistory writeWithMetadata(Long userId,
                                                 Long tenantId,
                                                 String eventId,
                                                 String type,
                                                 String channel,
                                                 String priority,
                                                 String provider,
                                                 String payload,
                                                 NotificationStatus status) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        return historyRepository.save(NotificationHistory.createWithMetadata(
                userId,
                tenantId,
                eventId,
                type,
                channel,
                priority,
                provider,
                payload,
                status
        ));
    }
}
