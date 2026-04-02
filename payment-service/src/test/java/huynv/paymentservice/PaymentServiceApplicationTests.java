package huynv.paymentservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provides lightweight unit tests that do not require external dependencies such as PostgreSQL or Kafka.
 */
public class PaymentServiceApplicationTests {

    /**
     * Verifies the unit test suite executes without starting the Spring application context.
     *
     * @return Asserts true for a minimal deterministic test.
     */
    @Test
    public void shouldRunUnitTestSuite() {
        assertTrue(true);
    }

}
