package huynv.gatewayservice.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Validates inbound request headers and rejects requests that contain obviously unsafe header values.
 */
@Component
public class HeaderValidationFilter implements GlobalFilter, Ordered {

    private static final List<Character> forbiddenCharacters = List.of('\r', '\n', '\0');

    /**
     * Rejects requests with header values containing CR/LF/null characters to prevent header injection abuses.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain or an error when validation fails.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                if (containsForbiddenCharacter(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request headers");
                }
            }
        });
        return chain.filter(exchange);
    }

    /**
     * Orders the validation filter early so malformed requests are rejected before expensive processing.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return -35;
    }

    /**
     * Checks whether a header value contains forbidden control characters.
     *
     * @param value Header value to validate.
     * @return Returns true when the header value contains forbidden control characters.
     */
    private static boolean containsForbiddenCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (forbiddenCharacters.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
