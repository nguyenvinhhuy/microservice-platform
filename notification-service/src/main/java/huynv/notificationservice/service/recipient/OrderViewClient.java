package huynv.notificationservice.service.recipient;

import huynv.notificationservice.exception.NonRetryableNotificationException;
import huynv.notificationservice.exception.RetryableDependencyException;
import huynv.eventinfra.resilience.ResilienceExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls order-view-service to resolve order identity information required for notification recipient resolution.
 */
@Component
public class OrderViewClient {

    private final RestClient orderViewRestClient;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Creates an order view client backed by RestClient.
     *
     * @param orderViewRestClient Rest client configured for order-view-service.
     * @param resilienceExecutor Resilience executor used to protect downstream calls.
     * @return Initializes an order view client.
     */
    public OrderViewClient(RestClient orderViewRestClient, ResilienceExecutor resilienceExecutor) {
        this.orderViewRestClient = Objects.requireNonNull(orderViewRestClient, "orderViewRestClient");
        this.resilienceExecutor = Objects.requireNonNull(resilienceExecutor, "resilienceExecutor");
    }

    /**
     * Resolves a user identifier for a given order identifier within a tenant.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param orderId Order identifier to resolve.
     * @return Returns the user identifier when the order exists and contains a user id.
     */
    public Optional<Long> resolveUserId(Long tenantId, UUID orderId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");
        return resilienceExecutor.execute("orderViewService", () -> doResolve(tenantId, orderId));
    }

    /**
     * Executes the actual REST call and translates HTTP errors into retryable or non-retryable exceptions.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param orderId Order identifier to resolve.
     * @return Returns the resolved user id when present, or an empty optional when the order is not found.
     */
    private Optional<Long> doResolve(Long tenantId, UUID orderId) {
        try {
            OrderViewResponse response = orderViewRestClient.get()
                    .uri("/orders/{id}", orderId)
                    .header("X-Tenant-Id", tenantId.toString())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(OrderViewResponse.class);
            if (response == null) {
                throw new RetryableDependencyException("Order view returned empty response orderId=" + orderId + ".", null);
            }
            if (response.userId() == null) {
                throw new RetryableDependencyException("Order view returned missing userId orderId=" + orderId + ".", null);
            }
            return Optional.of(response.userId());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                throw new RetryableDependencyException("Order view has not yet materialized orderId=" + orderId + ".", ex);
            }
            if (status >= 400 && status < 500) {
                throw new NonRetryableNotificationException("Order view rejected request status=" + status + " orderId=" + orderId + ".", ex);
            }
            throw new RetryableDependencyException("Order view failed status=" + status + " orderId=" + orderId + ".", ex);
        } catch (RetryableDependencyException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RetryableDependencyException("Order view call failed orderId=" + orderId + ".", ex);
        }
    }

    /**
     * Represents the subset of order-view-service response fields required for recipient resolution.
     *
     * @param orderId Order identifier.
     * @param userId User identifier associated with the order.
     */
    public record OrderViewResponse(UUID orderId, Long userId) {
    }
}

