package huynv.fileservice;

import huynv.eventinfra.config.SchedulingConfig;
import huynv.eventinfra.config.ShedLockConfig;
import huynv.eventinfra.outbox.KafkaOutboxMessage;
import huynv.eventinfra.outbox.KafkaOutboxPublisher;
import huynv.eventinfra.outbox.KafkaOutboxRepository;
import huynv.eventinfra.resilience.ResilienceExecutor;
import huynv.eventinfra.resilience.ResilienceExecutorsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@Import({SchedulingConfig.class, ShedLockConfig.class, KafkaOutboxPublisher.class, ResilienceExecutor.class, ResilienceExecutorsConfig.class})
@EntityScan(basePackageClasses = {FileServiceApplication.class, KafkaOutboxMessage.class})
@EnableJpaRepositories(basePackageClasses = {FileServiceApplication.class, KafkaOutboxRepository.class})
public class FileServiceApplication {

    /**
     * Starts the file-service Spring Boot application.
     *
     * @param args Command-line arguments provided by the runtime.
     * @return Performs a side effect by bootstrapping the Spring application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }

}
