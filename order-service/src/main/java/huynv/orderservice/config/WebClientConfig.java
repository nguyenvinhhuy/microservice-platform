package huynv.orderservice.config;

import huynv.orderservice.tracing.OtelPropagationExchangeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Creates a WebClient configured for inventory-service calls with safe timeouts.
     *
     * @param properties Inventory client properties containing base URL configuration.
     * @return Returns a WebClient configured for inventory-service requests.
     */
    @Bean
    public WebClient inventoryWebClient(InventoryClientProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(OtelPropagationExchangeFilter.create())
                .build();
    }

    /**
     * Creates a WebClient configured for payment-service calls with safe timeouts.
     *
     * @param properties Payment client properties containing base URL configuration.
     * @return Returns a WebClient configured for payment-service requests.
     */
    @Bean
    public WebClient paymentWebClient(PaymentClientProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(OtelPropagationExchangeFilter.create())
                .build();
    }

    /**
     * Creates a WebClient configured for product-service calls with safe timeouts.
     *
     * @param properties Product client properties containing base URL configuration.
     * @return Returns a WebClient configured for product-service requests.
     */
    @Bean
    public WebClient productWebClient(ProductClientProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(OtelPropagationExchangeFilter.create())
                .build();
    }
}
