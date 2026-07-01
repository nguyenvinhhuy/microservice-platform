package huynv.gatewayservice.filters;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class HttpsEnforcementFilterTest {

  /**
   * Verifies that when HTTPS enforcement is disabled, all requests pass through the filter chain
   * regardless of the request scheme.
   *
   * @return void — asserts that the filter completes normally for a plain-HTTP request when enforcement is off.
   */
  @Test
  void filter_httpsNotRequired_alwaysPassesThrough() {
    HttpsEnforcementFilter filter = new HttpsEnforcementFilter(false);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("http://example.com/test").build());

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();
  }

  /**
   * Verifies that when HTTPS enforcement is enabled, a request carrying the
   * {@code X-Forwarded-Proto: https} header is treated as secure and passes through the filter chain.
   *
   * @return void — asserts that the filter completes normally when the forwarded-protocol header indicates HTTPS.
   */
  @Test
  void filter_httpsRequired_forwardedProtoIsHttps_passesThrough() {
    HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://example.com/test")
                .header(GatewayHeaderNames.X_FORWARDED_PROTO, "https")
                .build());

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();
  }

  /**
   * Verifies that when HTTPS enforcement is enabled, a request whose URI scheme is already
   * {@code https} passes through the filter chain without error.
   *
   * @return void — asserts that the filter completes normally for a native HTTPS request.
   */
  @Test
  void filter_httpsRequired_schemeIsHttps_passesThrough() {
    HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("https://example.com/test").build());

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();
  }

  /**
   * Verifies that when HTTPS enforcement is enabled, a plain HTTP request that does not carry
   * an {@code X-Forwarded-Proto: https} header is rejected with an HTTP 426 Upgrade Required error.
   *
   * @return void — asserts that the reactive pipeline terminates with a {@link ResponseStatusException}
   *     whose status code is {@link org.springframework.http.HttpStatus#UPGRADE_REQUIRED}.
   */
  @Test
  void filter_httpsRequired_httpScheme_returnsUpgradeRequired() {
    HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("http://example.com/test").build());

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty()))
        .verifyErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(ResponseStatusException.class);
              assertThat(((ResponseStatusException) e).getStatusCode())
                  .isEqualTo(HttpStatus.UPGRADE_REQUIRED);
            });
  }

  /**
   * Verifies that the filter's order value is {@code -40}, making it the earliest-executing filter
   * in the chain so that insecure requests are blocked before any other processing.
   *
   * @return void — asserts that {@link HttpsEnforcementFilter#getOrder()} returns {@code -40}.
   */
  @Test
  void getOrder_returnsNegative40() {
    assertThat(new HttpsEnforcementFilter(false).getOrder()).isEqualTo(-40);
  }
}
