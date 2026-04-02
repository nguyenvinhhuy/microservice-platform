package huynv.productviewservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the product-view-service which maintains a denormalized product read model from Kafka events.
 */
@SpringBootApplication
public class ProductViewServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args Command-line arguments provided to the JVM process.
     * @return Performs a side effect by starting the application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(ProductViewServiceApplication.class, args);
    }
}

