package huynv.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.cloud.compatibility-verifier.enabled=false")
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
