package huynv.dlqreplayerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the DLQ replayer service which stores dead-lettered events for manual inspection and replay.
 */
@SpringBootApplication
public class DlqReplayerServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args Command-line arguments provided to the JVM process.
     * @return Performs a side effect by starting the application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(DlqReplayerServiceApplication.class, args);
    }
}

