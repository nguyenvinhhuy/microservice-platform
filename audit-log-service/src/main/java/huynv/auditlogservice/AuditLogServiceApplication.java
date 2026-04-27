package huynv.auditlogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditLogServiceApplication {

    /**
     * Bootstraps the audit log service Spring Boot application.
     *
     * @param args Command-line arguments passed to the Spring Boot application context.
     * @return Performs a side effect by starting the embedded server and initializing all beans.
     */
    public static void main(String[] args) {
        SpringApplication.run(AuditLogServiceApplication.class, args);
    }

}
