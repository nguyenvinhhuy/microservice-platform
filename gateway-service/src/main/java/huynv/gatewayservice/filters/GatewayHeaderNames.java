package huynv.gatewayservice.filters;

/**
 * Declares canonical header names used by the gateway for identity, correlation, and tracing propagation.
 */
public final class GatewayHeaderNames {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String ROLES = "X-Roles";
    public static final String TRACEPARENT = "traceparent";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    private GatewayHeaderNames() {
    }
}

