package huynv.orderviewservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the order-view-service which maintains a denormalized order read model from Kafka events.
 */
@SpringBootApplication
public class OrderViewServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args Command-line arguments provided to the JVM process.
     * @return Performs a side effect by starting the application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderViewServiceApplication.class, args);
    }
}

