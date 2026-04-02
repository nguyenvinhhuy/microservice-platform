package huynv.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boots the Spring application for the notification-service runtime.
 */
@SpringBootApplication(scanBasePackages = {"huynv.notificationservice", "huynv.eventinfra"})
@EntityScan(basePackages = {"huynv.notificationservice.domain", "huynv.eventinfra.outbox"})
@EnableJpaRepositories(basePackages = {"huynv.notificationservice.repository", "huynv.eventinfra.outbox"})
public class NotificationServiceApplication {

    /**
     * Starts the Spring Boot application context and begins processing notification workloads.
     *
     * @param args Command-line arguments used to configure the Spring Boot application.
     * @return Starts the Spring application and enables Kafka consumers, scheduling, and HTTP endpoints.
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
