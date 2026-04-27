# Gateway Service Dependencies

## Runtime Dependencies

| Dependency | Purpose |
| --- | --- |
| Spring Cloud Gateway WebFlux | HTTP routing and filter chain. |
| Resilience4j Circuit Breaker | Downstream fallback behavior. |
| Spring Security | Authentication and authorization. |
| OAuth2 Resource Server | JWT validation against Keycloak issuer. |
| Reactive Redis | Request rate limiting state. |
| Micrometer Prometheus | Gateway metrics export. |
| Logstash Logback Encoder | Structured JSON logging. |

## Inbound Interfaces

### External HTTP Entry Points

| Public Path Prefix | Downstream |
| --- | --- |
| `/api/users` | `user-service` |
| `/api/orders` | `order-service` |
| `/api/files` | `file-service` |
| `/api/notifications` | `notification-service` |
| `/api/audit` | `audit-log-service` |
| `/api/products` | `product-service` |
| `/api/payments` | `payment-service` |
| `/api/inventory` | `inventory-service` |
| `/api/views/products` | `product-view-service` |
| `/api/views/orders` | `order-view-service` |
| `/api/admin/dlq` | `dlq-replayer-service` |

### Internal Fallback Endpoint

| Method | Path | Purpose |
| --- | --- | --- |
| `ANY` | `/__gateway/fallback/{routeId}` | Standardized circuit-breaker fallback response. |

## Outbound Interfaces

### HTTP Downstream Services

| Route Id | Default URI |
| --- | --- |
| `user-service` | `http://user-service:8001` |
| `order-service` | `http://order-service:8002` |
| `file-service` | `http://file-service:8003` |
| `notification-service` | `http://notification-service:8004` |
| `audit-log-service` | `http://audit-log-service:8005` |
| `product-service` | `http://product-service:8006` |
| `payment-service` | `http://payment-service:8007` |
| `inventory-service` | `http://inventory-service:8008` |
| `product-view-service` | `http://product-view-service:8010` |
| `order-view-service` | `http://order-view-service:8011` |
| `dlq-replayer-service` | `http://dlq-replayer-service:8012` |

## Security Contract

Trusted downstream headers injected by gateway:

- `X-Request-Id`
- `X-Correlation-Id`
- `traceparent`
- `X-Trace-Id`
- `X-User-Id`
- `X-Tenant-Id`
- `X-Roles`

Notes:

- `X-Request-Id` and `X-Correlation-Id` are tracing/correlation headers.
- `Idempotency-Key` is not generated or normalized by the gateway; command services own that business contract.

Headers stripped from client and replaced:

- `X-User-Id`
- `X-Tenant-Id`
- `X-Roles`

## Operational Dependencies

| Dependency | Purpose |
| --- | --- |
| Keycloak issuer | JWT validation. |
| Redis | Rate limiting buckets. |

## Notable Constraints

- Admin routes require `ROLE_ADMIN`.
- Retry is restricted to `GET` and `HEAD`.
- Rate limit key uses JWT claims when available, otherwise IP fallback.
