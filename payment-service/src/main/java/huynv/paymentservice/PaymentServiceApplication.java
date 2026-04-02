package huynv.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import huynv.paymentservice.config.PaymentProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentServiceApplication {

    /**
     * Boots the Payment Service Spring Boot application.
     *
     * @param args Command line arguments passed to the application.
     * @return Starts the Spring application context and web server.
     */
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
