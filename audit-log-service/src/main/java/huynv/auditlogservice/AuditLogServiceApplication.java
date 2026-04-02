package huynv.auditlogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditLogServiceApplication {

    /**
     * main operation.
     *
     * @param args input parameter
     * @return performs side effects defined by this operation
     */
    public static void main(String[] args) {
        SpringApplication.run(AuditLogServiceApplication.class, args);
    }

}
