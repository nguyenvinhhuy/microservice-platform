package huynv.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayServiceApplication {

    /**
     * Boots the gateway service application that routes traffic to downstream microservices.
     *
     * @param args Command-line arguments provided to the JVM process.
     * @return Performs a side effect by starting the Spring application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}
