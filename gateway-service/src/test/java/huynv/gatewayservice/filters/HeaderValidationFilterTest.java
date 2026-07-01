package huynv.gatewayservice.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeaderValidationFilterTest {

  @Mock private ServerWebExchange exchange;
  @Mock private ServerHttpRequest request;

  private HeaderValidationFilter filter;

  /**
   * Initialises the {@link HeaderValidationFilter} and stubs the exchange-to-request relationship
   * with a lenient mock so that tests that do not exercise request headers do not trigger unnecessary stubbing.
   *
   * @return void — sets up the filter and mock stubs before each test method.
   */
  @BeforeEach
  void setUp() {
    filter = new HeaderValidationFilter();
    // Lenient: getOrder test does not use exchange/request
    lenient().when(exchange.getRequest()).thenReturn(request);
  }

  /**
   * Verifies that a request carrying only well-formed headers passes through the filter chain
   * without any exception being thrown.
   *
   * @return void — asserts that the filter completes normally when all header values are clean.
   */
  @Test
  void filter_cleanHeaders_chainIsCalled() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Custom", "clean-value");
    when(request.getHeaders()).thenReturn(headers);

    assertThatCode(() -> filter.filter(exchange, e -> Mono.empty()).block())
        .doesNotThrowAnyException();
  }

  /**
   * Verifies that a header value containing a carriage-return character is rejected with an
   * HTTP 400 Bad Request response to prevent HTTP response-splitting attacks.
   *
   * @return void — asserts that a {@link ResponseStatusException} with status 400 is thrown.
   */
  @Test
  void filter_headerWithCarriageReturn_throwsBadRequest() {
    when(request.getHeaders()).thenReturn(headersWithForbiddenValue("X-Bad", "value\rbad"));

    assertThatThrownBy(() -> filter.filter(exchange, e -> Mono.empty()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  /**
   * Verifies that a header value containing a line-feed character is rejected with an
   * HTTP 400 Bad Request response to prevent HTTP response-splitting attacks.
   *
   * @return void — asserts that a {@link ResponseStatusException} with status 400 is thrown.
   */
  @Test
  void filter_headerWithLineFeed_throwsBadRequest() {
    when(request.getHeaders()).thenReturn(headersWithForbiddenValue("X-Bad", "value\nbad"));

    assertThatThrownBy(() -> filter.filter(exchange, e -> Mono.empty()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  /**
   * Verifies that a header value containing a null character is rejected with an HTTP 400 Bad Request
   * response to prevent header-injection and null-byte injection attacks.
   *
   * @return void — asserts that a {@link ResponseStatusException} with status 400 is thrown.
   */
  @Test
  void filter_headerWithNullChar_throwsBadRequest() {
    when(request.getHeaders()).thenReturn(headersWithForbiddenValue("X-Bad", "value\0bad"));

    assertThatThrownBy(() -> filter.filter(exchange, e -> Mono.empty()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  /**
   * Verifies that the filter's order value is {@code -35}, placing it early in the filter chain
   * so that malformed headers are rejected before most other processing occurs.
   *
   * @return void — asserts that {@link HeaderValidationFilter#getOrder()} returns {@code -35}.
   */
  @Test
  void getOrder_returnsNegative35() {
    assertThat(filter.getOrder()).isEqualTo(-35);
  }

  /**
   * Builds an {@link HttpHeaders} instance containing a single header whose value is intended
   * to carry a forbidden control character for use in negative validation tests.
   *
   * @param headerName the HTTP header name to add.
   * @param value the raw header value, which may contain a forbidden character.
   * @return an {@link HttpHeaders} instance populated with the given name-value pair.
   */
  private static HttpHeaders headersWithForbiddenValue(String headerName, String value) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(headerName, value);
    return headers;
  }
}
