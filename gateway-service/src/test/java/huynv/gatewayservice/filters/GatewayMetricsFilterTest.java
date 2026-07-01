package huynv.gatewayservice.filters;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayMetricsFilterTest {

  private SimpleMeterRegistry meterRegistry;
  private GatewayMetricsFilter filter;

  /**
   * Initialises a fresh {@link SimpleMeterRegistry} and a new {@link GatewayMetricsFilter} instance
   * before each test to ensure metric counters start at zero.
   *
   * @return void — sets up the meter registry and filter fields before each test method.
   */
  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    filter = new GatewayMetricsFilter(meterRegistry);
  }

  /**
   * Verifies that a successful 2xx response increments the request counter with the {@code 2xx}
   * status-class tag and does not create an error counter.
   *
   * @return void — asserts the request counter equals one and no error counter exists in the registry.
   */
  @Test
  void filter_2xxResponse_incrementsRequestCounterOnly() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    exchange.getResponse().setStatusCode(HttpStatus.OK);

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

    assertThat(
            meterRegistry
                .counter("gateway_requests_total", "routeId", "unknown", "statusClass", "2xx")
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("gateway_errors_total").counter()).isNull();
  }

  /**
   * Verifies that a 5xx server error response increments both the request counter and the error
   * counter, each tagged with the {@code 5xx} status class.
   *
   * @return void — asserts both the request counter and the error counter equal one.
   */
  @Test
  void filter_5xxResponse_incrementsBothRequestAndErrorCounters() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

    assertThat(
            meterRegistry
                .counter("gateway_requests_total", "routeId", "unknown", "statusClass", "5xx")
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .counter("gateway_errors_total", "routeId", "unknown", "statusClass", "5xx")
                .count())
        .isEqualTo(1.0);
  }

  /**
   * Verifies that an HTTP 429 Too Many Requests response increments the error counter tagged with
   * the {@code 4xx} status class, treating rate-limit rejections as tracked errors.
   *
   * @return void — asserts the error counter for the 4xx status class equals one.
   */
  @Test
  void filter_429Response_incrementsErrorCounter() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

    assertThat(
            meterRegistry
                .counter("gateway_errors_total", "routeId", "unknown", "statusClass", "4xx")
                .count())
        .isEqualTo(1.0);
  }

  /**
   * Verifies that a generic 4xx client-error response (other than 429) increments only the request
   * counter and does not create an error counter.
   *
   * @return void — asserts the request counter equals one and no error counter exists in the registry.
   */
  @Test
  void filter_4xxResponse_incrementsRequestCounterOnly() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

    assertThat(
            meterRegistry
                .counter("gateway_requests_total", "routeId", "unknown", "statusClass", "4xx")
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("gateway_errors_total").counter()).isNull();
  }

  /**
   * Verifies that when the response status code is null (e.g. the exchange was never completed),
   * the request is recorded with the {@code unknown} status-class tag rather than throwing an exception.
   *
   * @return void — asserts the request counter with the {@code unknown} status-class tag equals one.
   */
  @Test
  void filter_nullStatusCode_recordsUnknownStatusClass() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    // response status intentionally left as null

    StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

    assertThat(
            meterRegistry
                .counter(
                    "gateway_requests_total", "routeId", "unknown", "statusClass", "unknown")
                .count())
        .isEqualTo(1.0);
  }

  /**
   * Verifies that the filter's order value is {@code 10}, placing it after high-priority security
   * and enforcement filters in the filter chain.
   *
   * @return void — asserts that {@link GatewayMetricsFilter#getOrder()} returns {@code 10}.
   */
  @Test
  void getOrder_returns10() {
    assertThat(filter.getOrder()).isEqualTo(10);
  }
}
