package huynv.notificationservice.config;

import huynv.eventinfra.config.NotificationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Objects;

/**
 * Configures REST clients for trusted internal service integrations.
 */
@Configuration
public class IntegrationConfig {

    /**
     * Creates a RestClient for calling order-view-service.
     *
     * @param properties Notification properties containing the order-view-service base URL.
     * @return Returns a RestClient configured for order-view-service calls.
     */
    @Bean
    public RestClient orderViewRestClient(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIntegrations().getConnectTimeoutMs()));
        requestFactory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIntegrations().getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getIntegrations().getOrderViewBaseUrl())
                .build();
    }

    /**
     * Creates a RestClient for calling user-service.
     *
     * @param properties Notification properties containing the user-service base URL.
     * @return Returns a RestClient configured for user-service calls.
     */
    @Bean
    public RestClient userServiceRestClient(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIntegrations().getConnectTimeoutMs()));
        requestFactory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIntegrations().getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getIntegrations().getUserServiceBaseUrl())
                .build();
    }
}
