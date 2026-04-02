package huynv.gatewayservice.filters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class W3cTraceContextTest {

    /**
     * Validates that a generated traceparent value can be parsed and yields a non-null trace id.
     *
     * @return Performs side effects by generating and parsing a traceparent header value under test.
     */
    @Test
    void shouldGenerateValidTraceparent() {
        String traceparent = W3cTraceContext.generateTraceparent();
        assertThat(W3cTraceContext.parseTraceId(traceparent)).isNotNull();
        assertThat(W3cTraceContext.traceIdFromTraceparent(traceparent)).hasSize(32);
    }

    /**
     * Validates that malformed traceparent values are rejected by the parser.
     *
     * @return Performs side effects by parsing invalid inputs and asserting null results.
     */
    @Test
    void shouldRejectMalformedTraceparent() {
        assertThat(W3cTraceContext.parseTraceId(null)).isNull();
        assertThat(W3cTraceContext.parseTraceId("")).isNull();
        assertThat(W3cTraceContext.parseTraceId("00-00000000000000000000000000000000-0000000000000000-01")).isNull();
        assertThat(W3cTraceContext.parseTraceId("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01")).isNull();
        assertThat(W3cTraceContext.parseTraceId("not-traceparent")).isNull();
    }
}

