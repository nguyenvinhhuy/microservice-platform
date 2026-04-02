package huynv.gatewayservice.filters;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides a safe, consistent fallback response for circuit breaker and downstream failure scenarios.
 */
@RestController
public class GatewayFallbackController {

    /**
     * Returns a standardized 503 response for circuit breaker fallbacks tied to a specific route identifier.
     *
     * @param routeId Route identifier that triggered the fallback.
     * @return Returns a JSON error payload describing the downstream unavailability.
     */
    @RequestMapping(path = "/__gateway/fallback/{routeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable("routeId") String routeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        payload.put("error", "Service Unavailable");
        payload.put("message", "Downstream service is temporarily unavailable");
        payload.put("routeId", routeId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
    }
}
