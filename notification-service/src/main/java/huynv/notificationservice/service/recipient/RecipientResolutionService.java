package huynv.notificationservice.service.recipient;

import huynv.notificationservice.exception.InvalidEventPayloadException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves recipient identity for events that do not contain direct user identity fields.
 */
@Service
public class RecipientResolutionService {

    private final OrderViewClient orderViewClient;

    /**
     * Creates a recipient resolution service backed by order-view-service lookups.
     *
     * @param orderViewClient Client used to resolve orderId to userId.
     * @return Initializes a recipient resolution service.
     */
    public RecipientResolutionService(OrderViewClient orderViewClient) {
        this.orderViewClient = Objects.requireNonNull(orderViewClient, "orderViewClient");
    }

    /**
     * Resolves a user identifier for a payment or order event using trusted service lookups.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier present in the event payload when available.
     * @param orderId Order identifier used for trusted lookup when userId is missing.
     * @return Returns the resolved user identifier when available.
     */
    public Optional<Long> resolveUserId(Long tenantId, Long userId, UUID orderId) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (userId != null) {
            return Optional.of(userId);
        }
        if (orderId == null) {
            throw new InvalidEventPayloadException("Event must contain userId or orderId for recipient resolution tenantId=" + tenantId + ".");
        }
        return orderViewClient.resolveUserId(tenantId, orderId);
    }
}
