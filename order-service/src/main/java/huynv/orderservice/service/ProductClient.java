package huynv.orderservice.service;

import huynv.orderservice.config.ProductClientProperties;
import huynv.orderservice.dto.ProductSnapshotResponse;
import huynv.orderservice.resilience.ResilienceExecutor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

/**
 * Calls product-service synchronously for product validation during order orchestration.
 */
@Component
public class ProductClient {

    private final WebClient productWebClient;
    private final ProductClientProperties properties;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Creates a product client backed by WebClient with resilience protections.
     *
     * @param productWebClient WebClient configured for product-service base URL and timeouts.
     * @param properties Product client properties containing endpoint paths.
     * @param resilienceExecutor Resilience executor used to apply CircuitBreaker, Retry, Timeout, and Bulkhead.
     * @return Initializes a product client instance.
     */
    public ProductClient(WebClient productWebClient, ProductClientProperties properties, ResilienceExecutor resilienceExecutor) {
        this.productWebClient = productWebClient;
        this.properties = properties;
        this.resilienceExecutor = resilienceExecutor;
    }

    /**
     * Loads a product snapshot by identifier for order validation.
     *
     * @param tenantId Tenant scope used for data isolation.
     * @param productId Product identifier to load.
     * @return Returns the product snapshot response.
     */
    public ProductSnapshotResponse getById(Long tenantId, Long productId) {
        String requestId = MDC.get("requestId");
        String traceId = MDC.get("traceId");
        return resilienceExecutor.execute("productService", () -> {
            try {
                return productWebClient.get()
                        .uri(properties.getGetByIdPath(), Map.of("id", productId))
                        .header("X-Tenant-Id", String.valueOf(tenantId))
                        .header("X-User-Id", String.valueOf(0L))
                        .header("X-Roles", "ROLE_SYSTEM")
                        .header("X-Request-Id", requestId != null ? requestId : UUID.randomUUID().toString())
                        .header("X-Trace-Id", traceId != null ? traceId : UUID.randomUUID().toString())
                        .retrieve()
                        .bodyToMono(ProductSnapshotResponse.class)
                        .block();
            } catch (WebClientResponseException ex) {
                throw new IllegalStateException("Product-service getById failed productId=" + productId + " status=" + ex.getStatusCode(), ex);
            }
        });
    }
}
