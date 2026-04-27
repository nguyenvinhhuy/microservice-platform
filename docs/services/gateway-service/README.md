# Gateway Service

## 1. Purpose

`gateway-service` là entry point HTTP duy nhất cho client bên ngoài. Nó chịu trách nhiệm:

- xác thực JWT
- áp policy authorization cấp gateway
- strip untrusted identity headers
- inject trusted request context cho downstream services
- apply rate limiting, safety limits, security headers
- route request tới microservice phù hợp
- fallback khi downstream unavailable

## 2. Key Functions

- Validate JWT từ Keycloak.
- Protect `/api/admin/**` bằng role `ADMIN`.
- Route request tới business services và read-model/admin services.
- Inject trusted tracing and identity headers:
  - `X-Request-Id`
  - `X-Correlation-Id`
  - `traceparent`
  - `X-Trace-Id`
  - `X-User-Id`
  - `X-Tenant-Id`
  - `X-Roles`
- Apply Redis-backed rate limiting.
- Enforce request size/time limits.
- Add response security headers.
- Emit structured logs và low-cardinality metrics.

Gateway chỉ chuẩn hóa header cho tracing/correlation và trusted identity. Business idempotency vẫn là contract của từng command service riêng, không phải trách nhiệm của gateway.

## 3. Security Boundary

### 3.1 Authentication

Gateway chạy như OAuth2 resource server.

- JWT issuer lấy từ `spring.security.oauth2.resourceserver.jwt.issuer-uri`
- mọi request, trừ actuator và `OPTIONS`, đều yêu cầu authentication
- `/api/admin/**` yêu cầu `ROLE_ADMIN`

### 3.2 Role Mapping

`KeycloakRolesGrantedAuthoritiesConverter` map role từ claim:

- `realm_access.roles`

thành Spring authorities dạng:

- `ROLE_<UPPERCASE_ROLE>`

### 3.3 Trusted Header Reinjection

`TrustedRequestContextFilter` là trust boundary chính.

Behavior:

1. strip inbound headers không được trust:
   - `X-User-Id`
   - `X-Tenant-Id`
   - `X-Roles`
2. ensure correlation headers:
   - `X-Request-Id`
   - `X-Correlation-Id`
   - `traceparent`
   - `X-Trace-Id`
3. lấy identity từ validated JWT:
   - `userId`, fallback `sub`
   - `tenantId`
   - roles từ authorities
4. inject lại headers trusted cho downstream

Lưu ý: `TrustedRequestContextFilter` không tạo hay chuẩn hóa `Idempotency-Key`. `X-Request-Id` chỉ là tracing header tại gateway boundary; business idempotency key phải được xử lý ở command service/API contract tương ứng.

Điểm này rất quan trọng: downstream services phải tin identity headers do gateway inject, không phải do external client gửi.

## 4. Route Map

### 4.1 Business Services

- `/api/users/**` -> `user-service`
- `/api/orders/**` -> `order-service`
- `/api/files/**` -> `file-service`
- `/api/notifications/**` -> `notification-service`
- `/api/audit/**` -> `audit-log-service`
- `/api/products/**` -> `product-service`
- `/api/payments/**` -> `payment-service`
- `/api/inventory/**` -> `inventory-service`

### 4.2 Read Models

- `/api/views/products/**` -> `product-view-service`
- `/api/views/orders/**` -> `order-view-service`

### 4.3 Admin Tooling

- `/api/admin/dlq/**` -> `dlq-replayer-service`

### 4.4 Prefix Handling

- business routes thường `StripPrefix=1`
- view/admin routes thường `StripPrefix=2`

## 5. Resilience Behavior

### 5.1 Circuit Breakers

Mỗi downstream route có `CircuitBreaker` riêng, fallback về:

- `/__gateway/fallback/{routeId}`

Fallback response:

- status `503`
- JSON body chứa `timestamp`, `status`, `error`, `message`, `routeId`

### 5.2 Retries

Retry chỉ được cấu hình cho các route idempotent/read-oriented:

- `user-service`
- `order-service`
- `product-service`
- `inventory-service`
- `product-view-service`
- `order-view-service`

Rule retry:

- methods: `GET`, `HEAD`
- statuses: `502`, `504`, `503`

Không retry các request command như `POST`, `PUT`, `PATCH`, `DELETE`.

## 6. Rate Limiting

Gateway dùng `RequestRateLimiter` của Spring Cloud Gateway với Redis.

Key resolver: `tenantUserRouteKeyResolver`

Key format ưu tiên:

- `tenant:<tenant>:user:<user>:route:<routeId>`
- `tenant:<tenant>:route:<routeId>`
- `user:<user>:route:<routeId>`
- fallback `ip:<ip>:route:<routeId>`

Điều này giúp rate limit bám theo tenant/user/endpoint thay vì chỉ IP.

## 7. Request Safety Filters

### 7.1 HTTPS Enforcement

`HttpsEnforcementFilter` có thể bật qua `gateway.security.require-https`.

- nếu bật, request non-HTTPS sẽ bị reject `426 UPGRADE_REQUIRED`

### 7.2 Header Validation

`HeaderValidationFilter` reject header chứa:

- CR
- LF
- null byte

Response:

- `400 BAD_REQUEST`

### 7.3 Request Size and Timeout

`RequestSafetyFilter` enforce:

- max request bytes: `gateway.request.max-bytes`
- hard timeout: `gateway.request.timeout`

Behavior:

- payload quá lớn -> `413 PAYLOAD_TOO_LARGE`
- request timeout -> `504 GATEWAY_TIMEOUT`

## 8. Response Hardening

`SecurityHeadersFilter` thêm:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`
- `Strict-Transport-Security` khi request là HTTPS

## 9. Observability

### 9.1 Structured Logs

`StructuredGatewayLoggingFilter` log:

- method
- path
- requestId
- traceId
- tenantId
- userId
- routeId
- status
- statusClass
- latencyMs

### 9.2 Metrics

`GatewayMetricsFilter` publish:

- `gateway_requests_total`
- `gateway_errors_total`
- `gateway_request_latency_seconds`

Tags:

- `routeId`
- `statusClass`

## 10. CORS

CORS config:

- `gateway.cors.allowed-origins`
- nếu `*` hoặc blank thì allow origin pattern `*`
- methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- headers: `*`
- `allowCredentials = false`

## 11. Technology and Runtime Notes

| Area | Value |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.0.2 |
| Gateway | Spring Cloud Gateway WebFlux |
| Spring Cloud | 2025.1.0 |
| Security | OAuth2 Resource Server |
| Rate Limiting | Redis reactive |
| Circuit Breaker | Resilience4j |
| Logging | Logstash JSON |

## 12. Known Limitations

- Java version của module là `17`, không khớp baseline repo `25`.
- Gateway chỉ đọc `tenantId` claim, không fallback `tenant_id`.
- Admin protection hiện chỉ ở `/api/admin/**`; các route khác chỉ cần authenticated, không có route-level RBAC chi tiết.
- Không có custom error envelope chung cho mọi gateway rejection; một phần dùng Spring default, một phần dùng fallback JSON riêng.
- Một số downstream URI/port trong route config có thể drift so với module runtime thực tế, nên route map phải luôn ưu tiên `application.yml`.

## 13. Related Artifacts

- [API spec](./api/gateway.yaml)
- [Dependencies](./dependencies.md)
