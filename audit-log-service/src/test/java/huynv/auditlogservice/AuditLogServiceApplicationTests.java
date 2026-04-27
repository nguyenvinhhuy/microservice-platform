package huynv.auditlogservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Smoke test verifying the application entry-point class can be referenced without errors.
 */
class AuditLogServiceApplicationTests {

    /**
     * Verifies that the application main class is loadable without throwing any exception.
     *
     * @return Performs a side effect by asserting the main class is accessible via reflection.
     */
    @Test
    void applicationClassLoads() {
        assertThatNoException().isThrownBy(() ->
                Class.forName("huynv.auditlogservice.AuditLogServiceApplication"));
    }

}
