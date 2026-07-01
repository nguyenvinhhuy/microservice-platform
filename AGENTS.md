# Engineering Conventions (Principal Standard)

## Scope
- Applies to all code generated or modified by agents in this repository.
- Preserve existing project structure and package layout.
- Keep changes minimal, deterministic, and production-safe.
- Treat this file as the single authoritative repository standard for architecture, coding conventions, verification, and agent behavior.

## Agent Read Protocol (Mandatory)
- Before making any code change, the agent must:
  1. Read `AGENTS.md` fully.
  2. Output a short `Rule-Check` summary listing the rules applied for the current task.
  3. Define pass/fail verification commands before editing.
- Before the final answer, the agent must output a short `Compliance Check` stating:
  - Which mandatory rules were checked.
  - Which commands were run.
  - Pass/fail result.

## JavaDoc Rule (Mandatory)
- Every non-trivial method and constructor must use a JavaDoc block directly above the declaration.
- If annotations are present, the JavaDoc block must be above the first annotation.
- The block must follow this exact structure and all placeholders must be replaced with meaningful text:
```java
/**
 * Describes the purpose of the method and the action it performs.
 *
 * @param <paramName> Description of the parameter.
 * @param <paramName> Description of another parameter (if applicable).
 * @return Description of the return value or side effects (even for void methods).
 */
```
- If the method is `void`, `@return` is still mandatory and must describe the side effects.
- Exactly one blank JavaDoc line (` *`) is required between the description and the first tag.
- The description line, every `@param`, and `@return` line must be full sentences ending with a period.
- Placeholder text is forbidden.
- Do not use old inline comment styles such as `// Function`, `// Param`, or `// Return`.
- Use one consistent full-sentence JavaDoc style repository-wide.

## Platform Architecture Standard

### Project Overview
- This repository hosts a production-grade microservice platform designed around isolated business capabilities, independent deployment, and operational resilience.
- Core domains include gateway, order, payment, inventory, product, notification, audit logging, and projection/read-model services.
- The platform is multi-tenant, event-driven, and built to tolerate retries, replays, partial downstream failures, and asynchronous consistency.

### Module Map & Ports

| Module | Port | DB Port | Role | In Root Reactor |
|---|---|---|---|---|
| `gateway-service` | 8000 | — | Edge gateway, auth propagation, routing, rate-limiting | yes |
| `user-service` | 8001 | 5435 | User management, preferences, addresses | **no** (standalone) |
| `order-service` | 8002 | 5436 | Orders + saga orchestration | yes |
| `file-service` | 8003 | 5437 | File upload, scan, storage (MinIO) | **no** (standalone) |
| `notification-service` | 8004 | 5438 | Async notification dispatch | yes |
| `audit-log-service` | 8005 | 5439 | Immutable audit trail | yes |
| `product-service` | 8006 | 5440 | Product catalogue (write model) | yes |
| `payment-service` | 8007 | 5441 | Payments, idempotency-key enforced | yes |
| `inventory-service` | 8008 | 5442 | Stock reservation / release | yes |
| `product-view-service` | 8010 | 5443 | Read-model projection for products | yes |
| `order-view-service` | 8011 | 5444 | Read-model projection for orders | yes |
| `dlq-replayer-service` | 8012 | 5445 | Controlled DLQ replay, operational tool | yes |
| `event-contract` | — | — | Shared event envelopes + JSON schemas | yes |
| `event-infra` | — | — | Kafka, outbox, retry, DLQ shared infra | yes |
| `angular-fe` | 80/8080 | — | Angular 21 frontend | n/a |

**Keycloak** runs on port **8180**, backed by `keycloak-db` on **5433**.

### Infrastructure Services

| Service | Image | Ports | Notes |
|---|---|---|---|
| Keycloak | quay.io/keycloak/keycloak | 8180 | OAuth2 / JWT issuer |
| Zookeeper | confluentinc/cp-zookeeper | 2181 | Kafka coordination |
| Kafka | confluentinc/cp-kafka | 9092 (external), 29092 (internal) | Message broker |
| Apicurio Schema Registry | apicurio/apicurio-registry | 8081 | Event schema validation |
| Redis | redis:7-alpine | 6379 | Rate-limiting, caching, idempotency |
| MinIO | minio/minio | 9000 (data), 9001 (console) | Object storage for file-service |
| Prometheus | prom/prometheus | 9090 | Metrics scraping |
| Grafana | grafana/grafana | 3000 | Dashboards |
| Jenkins | jenkins/jenkins:lts | 8088 | CI/CD |
| Docker Registry | registry:2 | 5000 | Local image registry |
| Databases | postgres:16-alpine | 5433–5445 | One per service, isolated |

Start order: infrastructure → keycloak → domain services → view services → gateway → angular-fe.

### Architecture Style

```
Browser / Mobile
      │
      ▼
 gateway-service :8000  ← Keycloak JWT validation, rate-limiting (Redis), circuit-breaker
      │
      ├── /api/users/**          → user-service :8001
      ├── /api/orders/**         → order-service :8002
      ├── /api/files/**          → file-service :8003
      ├── /api/notifications/**  → notification-service :8004
      ├── /api/audit/**          → audit-log-service :8005
      ├── /api/products/**       → product-service :8006
      ├── /api/payments/**       → payment-service :8007
      ├── /api/inventory/**      → inventory-service :8008
      ├── /api/views/products/** → product-view-service :8010
      ├── /api/views/orders/**   → order-view-service :8011
      └── /api/admin/dlq/**      → dlq-replayer-service :8012

Event flow (Kafka):
  order-service ──order.events──► order-view-service
                                ► notification-service
                                ► audit-log-service
                                ► payment-service (payment events reply)
  inventory-service ──inventory.events──► order-view-service
                                        ► audit-log-service
  product-service ──product.events──► product-view-service
                                    ► audit-log-service
  payment-service ──payment.events──► order-view-service
                                    ► notification-service
                                    ► audit-log-service
  user-service ──user.events──► audit-log-service
  file-service ──file.events──► audit-log-service
```

- External traffic enters through `gateway-service`.
- Synchronous service interaction uses REST and, where appropriate, gRPC.
- Asynchronous integration uses Kafka with canonical event envelopes from `event-contract`.
- Business events must be emitted through the transactional outbox pattern.
- Domain services own their own persistence and must not read or write each other's databases directly.
- Projection services such as `order-view-service` and `product-view-service` exist to support query-optimized read models.
- All domain events go through the **transactional outbox** — never direct `KafkaTemplate.send()` in transactional business code.
- All Kafka consumers check `processed_events(event_id, consumer_service)` via `JdbcIdempotencyService` to prevent duplicate processing.
- Failed messages → `<topic>-dlq`, replayed via `dlq-replayer-service` (configuration-gated, opt-in only).

### Gateway Routes

All routes have: `StripPrefix` filter, `CircuitBreaker` (Resilience4j: 50% failure threshold, 10s open wait), global rate-limiter (Redis, 50 replenish / 100 burst per tenant/user).

| Route | Incoming Path | Backend | Extra Filters |
|---|---|---|---|
| user-service | `/api/users/**` | `http://user-service:8001` | Retry GET/HEAD (max 2, 50ms–500ms backoff) |
| order-service | `/api/orders/**` | `http://order-service:8002` | Retry GET/HEAD |
| file-service | `/api/files/**` | `http://file-service:8003` | — |
| notification-service | `/api/notifications/**` | `http://notification-service:8004` | — |
| audit-log-service | `/api/audit/**` | `http://audit-log-service:8005` | — |
| product-service | `/api/products/**` | `http://product-service:8006` | Retry GET/HEAD |
| payment-service | `/api/payments/**` | `http://payment-service:8007` | — |
| inventory-service | `/api/inventory/**` | `http://inventory-service:8008` | Retry GET/HEAD |
| product-view-service | `/api/views/products/**` | `http://product-view-service:8010` | StripPrefix(2), Retry GET/HEAD |
| order-view-service | `/api/views/orders/**` | `http://order-view-service:8011` | StripPrefix(2), Retry GET/HEAD |
| dlq-replayer-service | `/api/admin/dlq/**` | `http://dlq-replayer-service:8012` | StripPrefix(2) |

Global: request size 10MB max, request timeout 10s, codec max-in-memory 2MB, CORS configurable via `gateway.cors.allowed-origins`.

### Tech Stack
- Backend: Spring Boot **4.1.0** (all services — single unified version), Java 25, Spring Data JPA, Spring Kafka, Spring Cloud Gateway, gRPC, ShedLock, Micrometer, OpenTelemetry.
- Frontend: Angular 21, TypeScript 5.9, RxJS, Tailwind CSS.
- Data and infrastructure: PostgreSQL per service, Redis, Kafka, Apicurio Registry (`schema-registry` in `docker-compose.yml`), MinIO, Docker, Kubernetes, Jenkins, Prometheus, Grafana, Keycloak.
- Build system: Maven aggregator at root `pom.xml`.
- Test libraries (platform standard — **never use JUnit 5.x**):
  - JUnit Jupiter: **6.0.3** — managed by Spring Boot 4.1.0 BOM, never declare explicit version in Spring Boot modules.
  - Mockito: **5.23.0** — managed by Spring Boot 4.1.0 BOM.
  - AssertJ: **3.27.7** — managed by Spring Boot 4.1.0 BOM.
  - Standalone modules without a Spring Boot parent (`event-contract`): explicitly pin `junit-jupiter:6.0.3`, `mockito-junit-jupiter:5.23.0`, `assertj-core:3.27.7`.
- Note on Spring Cloud: Spring Cloud `2025.1.x` is the current compatible train for Spring Boot 4.1.x. Tests that load a full Spring Cloud context must set `spring.cloud.compatibility-verifier.enabled=false` (use `@TestPropertySource` or `src/test/resources/application-test.properties`).

### Repository Structure
- `angular-fe/`: Frontend application and browser-facing assets.
- `gateway-service/`: Edge gateway, auth propagation, rate limiting, routing, and cross-cutting edge policy.
- `<domain>-service/`: Domain-owned write services and business logic.
- `*-view-service/`: Query-optimized read-model projections.
- `dlq-replayer-service/`: Controlled replay of dead-letter messages.
- `event-contract/`: Shared event envelopes, payloads, and JSON schemas.
- `event-infra/`: Shared Kafka, outbox, retry, and DLQ support infrastructure.
- `docs/services/<service>/README.md`: Implementation-accurate service guides for the current core services; prefer these over older high-level module `README.md` files when behavior conflicts.
- `user-service/` and `file-service/`: Legacy/standalone service modules still routed by `gateway-service`, but not included in the root Maven aggregator `pom.xml` or the `docs/services/` set.
- `docs/`: Architecture, service, API, event, and database documentation.
- `monitoring/`: Prometheus and Grafana assets.
- `k8s/`: Kubernetes manifests and deployment descriptors.

### REST API Reference

#### Order Service — `:8002`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `POST` | `/api/orders` | **required** | Creates order, starts saga (RESERVE_STOCK) |
| `POST` | `/api/orders/{orderId}/pay` | **required** | Advances saga to CHARGE_PAYMENT |
| `POST` | `/api/orders/{orderId}/cancel` | **required** | Cancels order, triggers compensation |

#### User Service — `:8001`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `GET` | `/api/users/me` | — | Current user profile |
| `PUT` | `/api/users/me` | **required** | Update profile |
| `GET` | `/api/users/{userId}` | — | Get user by ID |
| `GET` | `/api/users` | — | Search users (email, status, role, page, size) |
| `GET` | `/api/users/preferences` | — | Get notification preferences |
| `PUT` | `/api/users/preferences` | **required** | Update notification preferences |
| `GET` | `/api/users/addresses` | — | List addresses |
| `POST` | `/api/users/addresses` | **required** | Create address |

#### Payment Service — `:8007`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `POST` | `/api/payments` | **required** | Process payment — already enforces `Idempotency-Key` |
| `GET` | `/api/payments/{paymentId}` | — | Get payment by ID |

#### Product Service — `:8006` (roles: `ROLE_ADMIN`, `ROLE_VIEWER`)

| Method | Path | Role | Idempotency-Key |
|---|---|---|---|
| `POST` | `/api/v1/products` | ADMIN | optional |
| `GET` | `/api/v1/products` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/{id}` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/code/{code}` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/slug/{slug}` | ADMIN, VIEWER | — |
| `PUT` | `/api/v1/products/{id}` | ADMIN | — |
| `DELETE` | `/api/v1/products/{id}` | ADMIN | — |
| `GET` | `/api/v1/products/search?keyword=X` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/status/{status}` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/category/{categoryId}` | ADMIN, VIEWER | — |
| `GET` | `/api/v1/products/price-range` | ADMIN, VIEWER | — |

#### Inventory Service — `:8008` (internal only)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/inventory/internal/reservations` | Reserve stock |
| `POST` | `/api/inventory/internal/reservations/{orderId}/confirm` | Confirm reservation |
| `POST` | `/api/inventory/internal/reservations/{orderId}/release` | Release reservation |

#### File Service — `:8003`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `POST` | `/api/files/upload` | — | Direct multipart upload |
| `POST` | `/api/files/presigned-upload` | — | Get presigned upload URL |
| `POST` | `/api/files/multipart/initiate` | **required** | Start multipart upload |
| `POST` | `/api/files/{fileId}/multipart/parts/{part}/presign` | — | Presign a part |
| `POST` | `/api/files/{fileId}/multipart/complete` | **required** | Complete multipart upload |
| `DELETE` | `/api/files/{fileId}/multipart` | **required** | Abort multipart upload |
| `POST` | `/api/files/{fileId}/confirm` | **required** | Confirm presigned upload |
| `GET` | `/api/files/{fileId}` | — | File metadata |
| `GET` | `/api/files` | — | List tenant files (paginated) |
| `GET` | `/api/files/{fileId}/presigned-download` | — | Presigned download URL |
| `POST` | `/api/files/{fileId}/download-ticket` | — | Create download ticket |
| `GET` | `/api/files/{fileId}/download` | — | Stream download |
| `GET` | `/api/files/download-tickets/{token}/download` | — | Download via ticket |
| `DELETE` | `/api/files/{fileId}` | **required** | Soft-delete file |

#### Notification Service — `:8004`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/notifications` | List notification history (`limit` query param) |
| `GET` | `/api/notifications/preferences` | Get notification preferences |

#### Audit Log Service — `:8005` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/audit-logs` | List logs (filters: `eventType`, `aggregateId`, `page`, `size`) |
| `GET` | `/api/audit-logs/{id}` | Get log by ID |
| `GET` | `/api/audit-logs/user/{userId}` | Logs for a user |
| `GET` | `/api/audit-logs/search` | Search (eventType, aggregateId, page, size) |

#### Order View Service — `:8011` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/views/orders` | List order views (optional `X-User-Id` header, page, size) |
| `GET` | `/api/views/orders/{id}` | Get order view |

#### Product View Service — `:8010` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/views/products` | List product views (page, size) |
| `GET` | `/api/views/products/{id}` | Get product view |

#### DLQ Replayer Service — `:8012`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/admin/dlq/events` | List DLQ events (status filter, page, size) |
| `POST` | `/api/admin/dlq/replay` | Replay event (body: `{id, overrideTopic?}`) |
| `POST` | `/api/admin/dlq/skip` | Skip event (body: `{id}`) |

### Event Contract Reference

All events extend `BaseEvent<T>` from `event-contract`. The envelope fields are:

```
eventId:String, eventType:String, source:String, eventTime:Instant,
aggregateId:String, aggregateVersion:long, dataSchema:String,
traceId:String, correlationId:String, causationId:String, data:T
```

JSON schemas live in `event-contract/src/main/resources/schemas/` and are validated at deserialization time.

#### Order Events (`order.events`)

| Class | Key Fields |
|---|---|
| `OrderCreatedEvent` | orderId, tenantId, userId, status, totalAmount, currency, timestamp |
| `OrderPaidEvent` | orderId, tenantId, userId, status, paymentId, timestamp |
| `OrderCancelledEvent` | orderId, tenantId, userId, status, timestamp |
| `OrderFailedEvent` | orderId, tenantId, userId, status, reason, timestamp |

#### Payment Events (`payment.events`)

| Class | Key Fields |
|---|---|
| `PaymentProcessingEvent` | orderId, paymentId, tenantId |
| `PaymentCompletedEvent` | orderId, paymentId, tenantId, transactionId |
| `PaymentFailedEvent` | orderId, paymentId, tenantId, reason |

#### Inventory Events (`inventory.events`)

| Class | Key Fields |
|---|---|
| `StockReservedEvent` | orderId, tenantId, amount, currency, paymentProvider, idempotencyKey, items[]{productId, quantity} |
| `StockConfirmedEvent` | orderId, tenantId, items[]{productId, quantity} |
| `StockReleasedEvent` | orderId, tenantId, items[]{productId, quantity} |
| `StockReservationFailedEvent` | orderId, tenantId, reason |
| `StockUpdatedEvent` | tenantId, productId, totalStock, reservedStock, availableStock |

#### Product Events (`product.events`)

| Class | Key Fields |
|---|---|
| `ProductUpdatedEvent` | tenantId, productId, code, name, price, currency |
| `ProductPriceUpdatedEvent` | tenantId, productId, price, currency |

#### User Events (`user.events`)

| Class | Key Fields |
|---|---|
| `UserCreatedEvent` | userId, keycloakUserId, tenantId, email, fullName, phoneNumber, avatarUrl, status, locale, timezone, createdAt |
| `UserUpdatedEvent` | userId, keycloakUserId, tenantId, email, fullName, phoneNumber, avatarUrl, status, locale, timezone, updatedAt |
| `UserPreferencesUpdatedEvent` | userId, tenantId, emailEnabled, smsEnabled, pushEnabled, marketingEnabled, language, updatedAt |
| `UserAddressCreatedEvent` | userId, tenantId, addressId, label, country, city, district, addressLine, postalCode, isDefault, createdAt |

#### File Events (`file.events`)

| Class | Key Fields |
|---|---|
| `FileUploadedEvent` | fileId, tenantId, ownerUserId, category, bucket, objectKey, originalFilename, contentType, sizeBytes, checksumSha256, visibility, uploadedAt |
| `FileAvailableEvent` | fileId, tenantId, ownerUserId, category, bucket, objectKey, contentType, sizeBytes, checksumSha256, visibility, availableAt |
| `FileDeletedEvent` | fileId, tenantId, ownerUserId, bucket, objectKey, deletedAt |
| `FileQuarantinedEvent` | fileId, tenantId, ownerUserId, malwareStatus, reason, quarantinedAt |
| `FileScanCompletedEvent` | fileId, tenantId, malwareStatus, reason, scanDurationMs, scannerName, timedOut, checksumBlacklisted, scannedAt |

### Kafka Topics

| Topic | Producer | Consumers | Notes |
|---|---|---|---|
| `order.events` | order-service | order-view-service, notification-service, audit-log-service, payment-service | Order lifecycle |
| `payment.events` | payment-service | order-view-service, notification-service, audit-log-service | Payment results |
| `inventory.events` | inventory-service | order-view-service, audit-log-service, notification-service | Stock changes |
| `product.events` | product-service | product-view-service, audit-log-service | Catalogue updates |
| `user.events` | user-service | audit-log-service | User profile changes |
| `file.events` | file-service | audit-log-service | File lifecycle |
| `notification.high` | notification-service | notification dispatcher | High-priority dispatch |
| `notification.normal` | notification-service | notification dispatcher | Normal-priority dispatch |
| `notification.low` | notification-service | notification dispatcher | Low-priority dispatch |
| `notification.email` | notification-dispatcher | email-worker | Email tasks |
| `notification.sms` | notification-dispatcher | sms-worker | SMS tasks |
| `notification.push` | notification-dispatcher | push-worker | Push tasks |
| `file.scan.results` | scan orchestrator | file-service | Scan completion |

**DLQ / Retry topics** (pattern `<topic>-dlq`, `<topic>.retry.1m / 5m / 30m`):
`order.events.dlq`, `inventory.events.dlq`, `inventory.events.retry`, `payment.events.dlq`, `notification.events.dlq`, `notification.retry.1m/5m/30m`, `user.events.dlq`, `file.events.dlq`

DLQ replay is opt-in, config-gated, and delegated to `dlq-replayer-service` / `DlqReplayService`. Never replay directly from code.

### Database Schemas

#### order-service — `order_db` (port 5436)

| Table | Purpose |
|---|---|
| `orders` | id, tenant_id, user_id, status, total_amount, currency, created_at, updated_at, **version** |
| `order_items` | order_id(FK), product_id, quantity, price_at_purchase |
| `order_payments` | order_id(PK), payment_id, provider, status, amount, created_at, updated_at |
| `idempotency_keys` | id, tenant_id, action, request_id, order_id, created_at |
| `order_sagas` | id, tenant_id, order_id, state, payment_provider, payment_id, request_id, retry_count, last_error, created_at, updated_at, **version** |
| `kafka_outbox` | id, topic, message_key, payload, headers_json, status, created_at, published_at, attempt_count |
| `processed_events` | event_id, consumer_service, processed_at |

#### user-service — `user_db` (port 5435)

| Table | Purpose |
|---|---|
| `users` | id, keycloak_user_id, tenant_id, email, full_name, phone_number, avatar_url, status, locale, timezone, created_at, updated_at, deleted_at, **version** |
| `user_preferences` | id, tenant_id, user_id, email/sms/push/marketing_enabled, language, **version** |
| `user_addresses` | id, tenant_id, user_id, label, country, city, district, address_line, postal_code, is_default, **version** |
| `user_memberships` | id, tenant_id, user_id, role, status, created_at |
| `kafka_outbox` | — |
| `processed_events` | — |

#### product-service — `product_db` (port 5440)

| Table | Purpose |
|---|---|
| `t_products` | id(BIGSERIAL), code(UNIQUE), name, slug(UNIQUE), short_description, description, brand, category_id, price, currency, status, thumbnail_url, rating_average, rating_count, tenant_id |
| `t_product_images` | product_id(FK), url, is_primary, sort_order |
| `t_product_attributes` | product_id(FK), name, value |
| `t_product_prices` | product_id(FK), price, currency, valid_from, valid_to |
| `product_outbox` | — |
| `shedlock` | — |
| `processed_events` | — |

#### inventory-service — `inventory_db` (port 5442)

| Table | Purpose |
|---|---|
| `inventory` | id(BIGSERIAL), product_id, total_stock, reserved_stock, tenant_id, **version** — UNIQUE(tenant_id, product_id) |
| `inventory_reservation` | reservation_id(UUID), order_id, tenant_id, status, expires_at, amount, currency, payment_provider, idempotency_key, correlation_id, trace_id |
| `inventory_reservation_item` | reservation_id(FK), product_id, quantity |
| `outbox_events` | event_id(UUID UNIQUE), event_type, partition_key, payload, status, retry_count, next_attempt_at, published_at, last_error |
| `shedlock` | — |

#### payment-service — `payment_db` (port 5441)

| Table | Purpose |
|---|---|
| `payments` | payment_id(UUID PK), order_id, amount, currency, status, provider, transaction_id, idempotency_key(UNIQUE), created_at, updated_at, **version** |
| `payment_outbox` | aggregate_type, aggregate_id, event_type, payload, published, publish_attempts, next_attempt_at, last_error |
| `processed_events` | event_id(UUID), consumer_service, processed_at — **Note**: `event_id` is PK here, `consumer_service` is metadata only |
| `shedlock` | — |

> **Payment exception**: `processed_events` uses `event_id` as PK (not composite). Inspect schema before extending payment consumers.

#### file-service — `file_db` (port 5437)

| Table | Purpose |
|---|---|
| `files` | id(UUID), tenant_id, owner_user_id, category, object_key(UNIQUE), bucket, original_filename, content_type, size_bytes, checksum_sha256, storage_provider, status, visibility, malware_scan_status, metadata_json, **version** |
| `file_access_audit` | file_id, actor_user_id, action, outcome, details |
| `file_quota` | tenant_id(PK), used_bytes, quota_bytes, **version** |
| `api_idempotency` | tenant_id, idempotency_key, request_path, request_hash, status, response_body, expires_at |
| `multipart_upload_sessions` | file_id, upload_id, status, initiated_at, expires_at |

#### notification-service — `notification_db` (port 5438)

| Table | Purpose |
|---|---|
| `notification_history` | tenant_id, user_id, event_type, status, created_at |
| `notification_preferences` | tenant_id, user_id, email/sms/push_enabled |
| `kafka_outbox` | UUID PK, topic, message_key, payload, claim_status, publish_status |
| `api_idempotency` | tenant_id, idempotency_key, request_path, status, response_body, expires_at |
| `shedlock` | — |

#### audit-log-service — `audit_db` (port 5439)

| Table | Purpose |
|---|---|
| `audit_log` | id(BIGSERIAL), event_id(VARCHAR 64, UNIQUE), event_type, source, tenant_id, user_id, aggregate_id, aggregate_type, correlation_id, causation_id, raw_payload, received_at |
| `processed_events` | — |

#### order-view-service — `order_view_db` (port 5444)

| Table | Purpose |
|---|---|
| `order_view` | (tenant_id + order_id) PK, user_id, status, payment_status, stock_status, total_price, created_at |

#### product-view-service — `product_view_db` (port 5443)

| Table | Purpose |
|---|---|
| `product_view` | (tenant_id + product_id) PK, name, price, stock, status, updated_at |

#### dlq-replayer-service — `dlq_replayer_db` (port 5445)

| Table | Purpose |
|---|---|
| `dlq_events` | id(BIGSERIAL), topic, partition, offset, message_key, payload, status, original_topic, created_at |
| `processed_events` | — |

### Key Configuration Classes

#### Shared (`event-infra`)

| Class | Purpose |
|---|---|
| `IdempotencyConfig` | `JdbcIdempotencyService` bean — `alreadyProcessed()` + `markProcessed()` |
| `KafkaConsumerConfig` | Centralized consumer group, deserialization, error handling |
| `TracingConfig` | OpenTelemetry auto-configuration + custom spans |
| `ShedLockConfig` | Distributed lock provider (JDBC-backed) |
| `WorkerExecutorConfig` | Thread pools for event workers |
| `ResilienceExecutorsConfig` | Circuit breaker, retry, bulkhead thread pools |
| `SchedulingConfig` | Task scheduling baseline |

#### order-service

| Class | Purpose |
|---|---|
| `KafkaConfig` | Producer + consumer setup |
| `OrderKafkaProducerConfig` | Order-specific producer template |
| `RedisConfig` | Caching (disabled by default: `REDIS_ENABLED=false`) |
| `SchemaRegistryConfig` | Apicurio schema registry client |
| `SecurityConfig` | JWT + UserContext filter |
| `ShedLockConfig` | Saga resume scheduler locking |
| `WebClientConfig` | WebClient for calling inventory/payment services |
| `RateLimitingConfig` | Token-bucket rate limiting |
| `ResilienceExecutorsConfig` | Async executors for circuit-breaker/retry |

#### user-service

| Class | Purpose |
|---|---|
| `CacheConfig` | Redis cache manager |
| `SecurityConfig` | JWT + `InternalAccessEvaluator` |

#### file-service

| Class | Purpose |
|---|---|
| `FileServiceConfig` | File lifecycle policy |
| `StorageConfig` | MinIO / S3 client |
| `CacheConfig` | Redis cache for presigned URLs + metadata |
| `SecurityConfig` | JWT + `InternalAccessEvaluator` |

#### product-service / inventory-service

| Class | Purpose |
|---|---|
| `KafkaTopicConfig` | Topic name constants |
| `SchemaRegistryConfig` | Schema registry client |
| `ShedLockConfig` | Scheduled job locking |
| `SecurityConfig` | `UserContextFilter` |

### Environment Variables

Key variables (from `.env.example` / `docker-compose.yml`):

```bash
# Databases (one per service)
USER_DB_PASSWORD, ORDER_DB_PASSWORD, FILE_DB_PASSWORD
NOTIFICATION_DB_PASSWORD, AUDIT_DB_PASSWORD, PRODUCT_DB_PASSWORD
PAYMENT_DB_PASSWORD, INVENTORY_DB_PASSWORD
PRODUCT_VIEW_DB_PASSWORD, ORDER_VIEW_DB_PASSWORD
DLQ_REPLAYER_DB_PASSWORD, KEYCLOAK_DB_PASSWORD

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092   # internal docker; 9092 externally
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
KAFKA_TOPIC_PREFIX=microservice

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# Storage (MinIO)
MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=

# Email
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=

# Keycloak / Identity
KEYCLOAK_DB=keycloak
KEYCLOAK_DB_USER=keycloak
KEYCLOAK_DB_PASSWORD=
KEYCLOAK_ADMIN=
KEYCLOAK_ADMIN_PASSWORD=
KEYCLOAK_ISSUER_URI=http://keycloak:8180/realms/<realm>

# Observability
OTEL_EXPORTER_OTLP_ENDPOINT=
PROMETHEUS_RETENTION_DAYS=15
GRAFANA_USER=admin
GRAFANA_PASSWORD=
GRAFANA_ADMIN_EMAIL=

# Application
JWT_SECRET=
JWT_EXPIRATION_HOURS=24
LOG_LEVEL=INFO
LOG_FORMAT=json
APP_ENVIRONMENT=local
```

Never commit `.env` to source control. Copy `.env.example` → `.env` and fill in secrets locally. Secrets in production go in Vault or Kubernetes Secrets.

## Engineering Standards (Mandatory)

### Naming and API Contract
- Use intention-revealing names and avoid vague abbreviations except common acronyms.
- Keep method signatures stable unless a bug fix or explicit contract change requires modification.
- Keep backward compatibility for public APIs and event payloads unless versioning is introduced deliberately.

### Security and Auth

**Gateway**: OAuth2 Resource Server with JWT validation (Keycloak as issuer). `/api/admin/**` requires `ROLE_ADMIN`. All other `/api/**` require authentication. JWT authority mapping: Keycloak realm roles → Spring `ROLE_` prefix. CORS via `gateway.cors.allowed-origins`.

**Service-level auth**:

| Service | Auth Method | Notes |
|---|---|---|
| order-service | OAuth2 JWT + `@PreAuthorize` | Roles: `USER`, `ADMIN` |
| user-service | OAuth2 JWT + `UserContextFilter` + `InternalAccessEvaluator` | Checks `azp` claim for service-to-service |
| product-service | `UserContextFilter` | Write = `ROLE_ADMIN`, Read = `ROLE_ADMIN` / `ROLE_VIEWER` |
| file-service | OAuth2 JWT + `InternalAccessEvaluator` | Internal callers validated via `azp` JWT claim |
| inventory-service | `UserContextFilter` | Internal endpoints only |
| notification-service | OAuth2 JWT | Standard |
| audit-log-service | `X-Tenant-Id` header | Tenant isolation by header |
| payment-service | No public exposure | Internal-only |
| view services | `X-Tenant-Id` header | Read-only, tenant isolation by header |
| dlq-replayer-service | Admin-only routing | Via gateway `/api/admin/**` |

**Allowed internal callers** (via `azp` JWT claim):
```
user-service.security.internal.allowed-authorized-parties:
  gateway-service, order-service, payment-service, inventory-service,
  product-service, notification-service, audit-log-service,
  file-service, order-view-service, product-view-service, dlq-replayer-service

file-service.security.internal.allowed-authorized-parties:
  gateway-service, notification-service
```

**Idempotency header per service**:

| Service | Header | Scope |
|---|---|---|
| order-service | `Idempotency-Key` | create, pay, cancel |
| user-service | `Idempotency-Key` | PUT me, PUT preferences, POST addresses |
| payment-service | `Idempotency-Key` | POST /api/payments (mandatory, stored in `payments.idempotency_key`) |
| file-service | `Idempotency-Key` | multipart initiate/complete/abort, confirm, delete |
| product-service | `Idempotency-Key` | POST (optional) |

> `X-Request-Id` is the transport-level tracing header — do **not** treat it as a business idempotency key for new work.

### Multi-Tenancy and Data Safety
- All business-data repository queries must be tenant-aware.
- Service logic must validate tenant ownership before mutating state.
- Never read or update cross-tenant data in request flow or background processing.
- `UserContextFilter` and trusted gateway context propagation define the service trust boundary for tenant and user identity.
- Services must not trust raw inbound identity headers from external clients.

### Request Idempotency
- State-changing REST endpoints such as create, pay, cancel, reserve, confirm, and release must enforce idempotency through a dedicated `Idempotency-Key` contract or a service-specific equivalent request field such as `idempotencyKey`.
- The typical implementation is:
  - Insert an idempotency row with `PROCESSING` at command start.
  - Return cached response when the row is already `COMPLETED`.
  - Return deterministic in-flight response when the row is still `PROCESSING`.
  - Update the row to `COMPLETED` or `FAILED` with serialized response data at the end.
- Idempotency rows should be purged after roughly 24 hours by a scheduled cleanup job.
- `Idempotency-Key` is the canonical business idempotency header for REST write APIs in new work.
- `X-Request-Id` is the transport-level request tracing header and should not be treated as the default business idempotency key.
- Current compatibility exceptions are explicitly scoped: `order-service` create/pay/cancel commands and `product-service` create temporarily accept `X-Request-Id` when `Idempotency-Key` is absent, while `payment-service` already requires `Idempotency-Key` for REST commands and persists it in `payments.idempotency_key`.

### Event-Driven Architecture
- All Kafka events are wrapped in `BaseEvent<T>` from `event-contract`.
- The canonical envelope contains `eventId`, `eventType`, `eventVersion`, `tenantId`, `correlationId`, `causationId`, `timestamp`, and `data`.
- Event schemas are classpath-loaded from `event-contract/src/main/resources/schemas/` and validated during deserialization.
- Consumers commonly use `@KafkaListener` in `@Component` classes with explicit parsing and may be gated by `@ConditionalOnProperty` when activation must be configurable.

### Consumer Idempotency
- Kafka consumers must check `processed_events(event_id, consumer_service)` before processing.
- Use `JdbcIdempotencyService` from `IdempotencyConfig` to call `alreadyProcessed(eventId)` and `markProcessed(eventId)` inside the same transaction as the business mutation.
- Failure to do this will cause duplicate processing during retries and rebalances.
- Current exception: `payment-service` documents `processed_events` with `event_id` as the primary key and `consumer_service` as metadata only, so inspect that module's schema before extending its consumers.

### Kafka Publishing and Outbox
- Never publish domain events directly to Kafka from transactional business code.
- Use the transactional outbox pattern:
  1. Persist the business entity change.
  2. Persist the corresponding `OutboxEvent` with `PENDING` status, topic, key, and serialized envelope.
  3. Commit both atomically.
- `KafkaOutboxPublisher` or equivalent scheduled publisher must poll `PENDING` rows, publish them, and move them to `SENT` on broker acknowledgement.
- At-least-once semantics are expected; if publishing succeeds but state update fails, the next poll may replay the same message with the same key.
- Outbox message keys should use stable tenant and entity identifiers to preserve ordering.

### Retry and DLQ
- Services must not retry indefinitely.
- Failed Kafka messages are routed to `<topic>-dlq` after the configured retry budget is exhausted.
- DLQ replay must be opt-in, configuration-gated, and delegated to supported replay components such as `DlqReplayService`.
- Retries must target transient failures only and should use exponential backoff where applicable.

### Saga Orchestration

Order-centric orchestration follows this state machine:

```
RESERVE_STOCK → CHARGE_PAYMENT → CONFIRM_STOCK → COMPLETED
                     ↓
               COMPENSATING  (refund + stock release)
```

| State | Action | On Success | On Failure |
|---|---|---|---|
| `RESERVE_STOCK` | Call inventory-service `/internal/reservations` | Advance to `CHARGE_PAYMENT` | → `COMPENSATING` |
| `CHARGE_PAYMENT` | Call payment-service `/api/payments` | Advance to `CONFIRM_STOCK` | → `COMPENSATING` |
| `CONFIRM_STOCK` | Call inventory-service `/internal/reservations/{id}/confirm` | → `COMPLETED` | → `COMPENSATING` |
| `COMPENSATING` | Refund payment + release stock (idempotent) | → `COMPLETED` | Retry with backoff |
| `COMPLETED` | Terminal — no action | — | — |

**Saga persistence** (`order_sagas` table):
```
tenant_id, order_id (UNIQUE together — one saga per order)
state, payment_provider, payment_id, request_id (idempotency)
retry_count, last_error, version (optimistic lock)
```

**Crash recovery**: Scheduled task (`@Scheduled fixedDelay=5s`) resumes non-terminal sagas (`RESERVE_STOCK`, `CHARGE_PAYMENT`, `CONFIRM_STOCK`, `COMPENSATING`). Uses **ShedLock** (`saga-resume-lock`) to prevent concurrent execution across replicas.

**Trigger flows**:
- `POST /api/orders` → create order → saga starts at `RESERVE_STOCK`
- `POST /api/orders/{id}/pay` → user triggers `CHARGE_PAYMENT`
- `POST /api/orders/{id}/cancel` → releases stock if state allows → order `CANCELLED`

Rules:
- Sagas are persisted in dedicated `<service>_sagas` tables with state, retry count, error details, and resumable step metadata.
- Each saga step must be isolated with `@Transactional(propagation = Propagation.REQUIRES_NEW)` or equivalent crash-safe transaction boundaries.
- Compensation must safely support refund and stock release when downstream steps fail.
- Scheduled saga resume tasks must execute idempotently and with distributed locking.

### Concurrency and Locking
- Optimistic locking using Hibernate `@Version` is mandatory for critical aggregates such as Order, Payment, and Product.
- Optimistic locking conflicts must map to HTTP 409.
- Use `SELECT ... FOR UPDATE SKIP LOCKED` or equivalent pessimistic locking only for critical concurrent batch workflows such as saga resume batches.
- Scheduled mutating jobs must use ShedLock with stable lock names.
- Reserve, confirm, release, and compensation flows must be idempotent and state-aware before mutation.

### Observability and Tracing
- Include `tenantId`, `orderId`, and `productId` in MDC whenever available; include `eventId`, `sagaId`, and `paymentId` where relevant.
- Always clear MDC and thread-local context after request or operation completion.
- Use structured logging and correlation-friendly messages.
- Services should auto-configure OpenTelemetry and create custom spans for critical business paths when necessary.
- `/actuator/health` and `/actuator/prometheus` must be exposed with meaningful probes and metrics.
- Outbox depth, DLQ depth, retry behavior, and consumer lag should be observable.

### Configuration and Secrets
- Configuration should be environment-driven and validated at startup.
- Keep local, test, staging, and production concerns separated through profiles or equivalent layering.
- Missing mandatory configuration must fail fast.
- Secrets belong in Vault or Kubernetes Secrets, never in source control.

## Coding Convention

### General Naming
- Use `camelCase` for methods, variables, fields, parameters, and JSON properties unless protocol standards require otherwise.
- Use `PascalCase` for classes, interfaces, enums, DTOs, records, mappers, Angular components, and services.
- Use `UPPER_CASE` for constants and environment variable names.

### File Naming
- Java public types must live in files with matching names.
- Angular and TypeScript files should use descriptive hyphen-case such as `order-summary.component.ts` or `payment-client.service.ts`.
- Avoid generic dump files such as `helper.ts`, `common.ts`, or `Utils.java` unless they are truly cohesive and unavoidable.

### DTO, Entity, Mapper
- DTOs define transport contracts only.
- Entities model persistence state and should not be exposed directly as external API responses.
- Mappers isolate conversion between DTOs, domain models, persistence models, and event payloads.
- Event payload changes are public contract changes and require compatibility review.

### Clean Architecture
- Keep controllers thin and delegate business decisions to service or application layers.
- Domain rules must not depend on HTTP, Kafka, UI, or driver concerns.
- Infrastructure adapters should stay isolated from core business logic.
- Prefer package structures that reflect business capability over generic technical dumping grounds.

### Exception Handling
- Never swallow exceptions silently.
- Convert low-level failures into meaningful domain or application exceptions where appropriate.
- Centralize error mapping for HTTP and async processing so responses remain consistent and machine-readable.

### Logging Rule
- Log at boundaries, state transitions, retries, fallbacks, compensation paths, and failure points.
- Do not log secrets, tokens, passwords, or sensitive PII.
- Avoid noisy loop logging where metrics are more appropriate.

### Java Standard
- Follow the mandatory JavaDoc rule for every non-trivial method and constructor.
- Prefer explicit transaction boundaries around business mutations.
- Prefer tenant-aware repository methods and explicit state checks before mutation.

### TypeScript and Angular Standard
- Prefer strict typing and avoid `any` unless the integration boundary truly requires it.
- Keep components focused on presentation and orchestration.
- Extract reusable coordination logic into services, facades, or stores.
- Avoid circular imports and keep feature folders cohesive.

## Linting and Formatting Standards

### ESLint
- Root `.eslintrc.js` is the frontend lint baseline for `angular-fe`.
- Use:
  - `@typescript-eslint`
  - `eslint-plugin-import`
  - `eslint-plugin-sonarjs`
  - `eslint-config-prettier`
- Enforce practical rules for import order, promise safety, duplicate imports, and unused variables.
- ESLint does not lint Java source; Java quality is enforced by repository conventions and build tooling.

### Prettier
- Root `.prettierrc` is the canonical formatter configuration.
- Prettier is the formatter for frontend, web, JSON, YAML, Markdown, and Java assets in this repository.
- Formatting baseline:
  - `semi: true`
  - `singleQuote: true`
  - `printWidth: 120`
  - `tabWidth: 2` by default, with `tabWidth: 4` for `*.java` via override.
  - `trailingComma: all`
- `.eslintignore` and `.prettierignore` must exclude generated and non-source directories such as `node_modules`, `dist`, `build`, and `coverage`.

### Java Formatting
- Root `.editorconfig` defines the shared editor whitespace baseline for Java and non-Java files.
- **Build-time canonical formatter for reactor modules**: Spotless with Google Java Format (`mvn spotless:apply` or `mvn verify`). This is authoritative — CI enforces it.
- **IDE / editor integration**: Root `.prettierrc` includes `prettier-plugin-java` with 4-space override for on-save formatting in IntelliJ. Point Prettier to `node_modules/prettier` and the root `.prettierrc`. These two formatters may produce subtly different output; treat Spotless/Google Java Format as the final arbiter for reactor modules.
- Standalone modules (`user-service`, `file-service`) are outside the root Maven reactor and are not covered by root Spotless; use module-local Maven commands or Prettier for those.

### Import Order
- External framework imports should appear before internal application imports.
- Use type-only imports in TypeScript when possible.
- Duplicate imports and circular imports are forbidden.

## Git Convention

### Branch Naming
- Use:
  - `feat/<scope>-<short-description>`
  - `fix/<scope>-<short-description>`
  - `refactor/<scope>-<short-description>`
  - `docs/<scope>-<short-description>`
  - `chore/<scope>-<short-description>`

### Commit Messages
- Use Conventional Commits.
- Examples:
  - `feat(order-service): add saga retry backoff policy`
  - `fix(payment-service): map optimistic locking to conflict response`
  - `docs(platform): optimize engineering standards`
  - `chore(frontend): add eslint and prettier baseline`

### Pull Request Rules
- One pull request should address one clear objective.
- The description must include what changed, why it changed, how it was verified, and any rollback concern.
- Contract, schema, topic, or public API changes must explicitly state compatibility impact.
- Do not merge while CI is failing or while unresolved architectural risks remain.

## Build, Verification, and Development Flow

### Build Commands

```bash
# Full reactor build + verify (includes Spotless format check)
mvn clean verify

# Single module (resolves dependencies first with -am)
mvn -q -pl order-service -am verify
mvn -q -pl order-service -am test

# Apply Java formatting (Spotless / Google Java Format) — run before committing
mvn spotless:apply

# Standalone modules (NOT in root reactor — run from their own directory)
cd user-service  && mvn clean verify
cd file-service  && mvn clean verify

# Frontend
cd angular-fe && npm install
cd angular-fe && npm run build
cd angular-fe && npm run test
cd angular-fe && npm run start   # dev server

# Infrastructure (all dependencies: Postgres×12, Kafka, Redis, MinIO, Keycloak, Apicurio, Prometheus, Grafana)
docker-compose up -d
docker-compose down

# Docs
# Per-service guides: docs/services/<service>/README.md  (authoritative — prefer over module README)
# Architecture:        docs/ARCHITECTURE.md
```

### Local Setup
1. Install Java 25, Maven, Node.js, Docker, and Docker Compose.
2. Create `.env` from `.env.example`.
3. Start shared infrastructure with `docker-compose up -d`.
4. Install frontend dependencies in `angular-fe` when frontend work is required.

### Verification Standard
- For each meaningful change, run at least module-level compile or tests.
- Keep test profiles isolated from external dependencies when practical.
- Add or adjust tests for bug-fix behavior when practical.
- If a full verification command is too expensive for the current task, run the narrowest command that still meaningfully validates the affected area.
- The committed frontend scripts in `angular-fe/package.json` are `start`, `build`, `watch`, and `test`; do not assume a working `npm run lint` script exists.
- The current root `Jenkinsfile` only installs, builds, tests, containerizes, and deploys `angular-fe` (`k8s/18-angular-fe.yaml`), so backend verification remains a separate responsibility.

## Common Patterns (Code Reference)

### Adding a new Kafka consumer

```java
@KafkaListener(topics = "${kafka.topics.some-topic}", groupId = "${spring.kafka.consumer.group-id}")
public void consume(ConsumerRecord<String, String> record) {
    BaseEvent<SomeData> event = parse(record);
    if (idempotencyService.alreadyProcessed(event.getEventId())) return;
    // business mutation inside same @Transactional
    idempotencyService.markProcessed(event.getEventId());
}
```

Always: parse → idempotency check → business mutation → mark processed — all in **one transaction**.

### Publishing via outbox

```java
// 1. Save business entity
repository.save(entity);
// 2. Save OutboxEvent in SAME transaction
outboxRepository.save(OutboxEvent.of(topic, key, envelope));
// Outbox publisher polls PENDING rows separately and publishes to Kafka
```

### Idempotency for REST commands

```java
// Check idempotency key at command start
IdempotencyRecord existing = idempotencyRepo.findByKey(idempotencyKey);
if (existing != null && existing.getStatus() == COMPLETED) return existing.getCachedResponse();
if (existing != null && existing.getStatus() == PROCESSING) return inFlightResponse();
idempotencyRepo.insert(idempotencyKey, PROCESSING);
// ... process ...
idempotencyRepo.update(idempotencyKey, COMPLETED, serializedResponse);
```

### Multi-tenancy guard

```java
// Always extract tenantId from UserContext (set by UserContextFilter), never from request body
String tenantId = UserContext.getTenantId();
// All repo calls must include tenantId
repository.findByIdAndTenantId(id, tenantId)
          .orElseThrow(() -> new NotFoundException(...));
```

### Adding a new event type

1. Create payload class in `event-contract/src/main/java/huynv/event/<domain>/`.
2. Create JSON schema in `event-contract/src/main/resources/schemas/<domain>.<action>.v1.json`.
3. Add event type constant to `<Domain>EventTypes`.
4. Run `mvn -pl event-contract verify` to validate.
5. Update producer service to wrap in `BaseEvent<T>` and save to outbox.
6. Update consumer service to register `@KafkaListener` with idempotency check.
7. Update `docs/services/<service>/README.md` with new event flow.

## Testing Standards (Mandatory)

> **Rule: NEVER use JUnit 5.x. The platform standard is JUnit 6.x. Always use the newest stable version.**

### All services use Spring Boot **4.1.0** (single unified version)

All modules — `user-service`, `file-service`, and all reactor modules — use Spring Boot **4.1.0** as the parent. The Spring Boot BOM manages test library versions automatically; **do not declare explicit versions** for the following:

| Library | Version (Spring Boot 4.1.0 BOM) |
|---|---|
| `junit-jupiter` | **6.0.3** |
| `mockito-core` / `mockito-junit-jupiter` | **5.23.0** |
| `assertj-core` | **3.27.7** |

### `event-contract` (standalone — no Spring Boot parent)

`event-contract` has no Spring Boot parent, so versions must be pinned **explicitly** in `event-contract/pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>6.0.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.27.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>5.23.0</version>
    <scope>test</scope>
</dependency>
```

### Mockito limitation in `event-contract`

`event-contract` has no ByteBuddy on its classpath. Mockito **cannot mock JDK interfaces** (e.g. `javax.sql.DataSource`, `java.sql.Connection`) in this module. Use H2 in-memory database for `JdbcIdempotencyService` tests instead:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.3.232</version>
    <scope>test</scope>
</dependency>
```

### Spring Cloud compatibility

Spring Cloud `2025.1.x` supports Spring Boot `4.0.x` and `4.1.x`. Tests that load a full Spring Cloud context (e.g. `gateway-service`) must disable the compatibility verifier:

```java
@SpringBootTest
@TestPropertySource(properties = "spring.cloud.compatibility-verifier.enabled=false")
class MyTests { ... }
```

Or via `src/test/resources/application-test.properties`:
```
spring.cloud.compatibility-verifier.enabled=false
```

### When adding test dependencies

1. Check that the Spring Boot parent BOM already manages the library — if yes, omit the version.
2. If the module has no Spring Boot parent, pin the latest stable version matching the table above.
3. Always verify the resolved version with `mvn dependency:tree -Dincludes="org.junit*,org.mockito*,org.assertj*"` if uncertain.

## Service Checklist (Mandatory Review Checklist)

Run through before marking any service-level work complete:

- [ ] Health checks exposed: `/actuator/health` (liveness + readiness) and `/actuator/prometheus`
- [ ] Graceful shutdown and request draining configured
- [ ] Structured logging enabled; MDC populated (`tenantId`, `orderId`, `productId`, `eventId`, `sagaId`, `paymentId` where relevant); MDC cleared after request/operation
- [ ] Prometheus metrics meaningful (outbox depth, DLQ depth, consumer lag, retry rate)
- [ ] All repository queries and mutations are **tenant-scoped**
- [ ] Write endpoints enforce idempotency (`Idempotency-Key` header or `idempotencyKey` field)
- [ ] Kafka consumers check `processed_events` via `JdbcIdempotencyService` before processing
- [ ] Events published via **transactional outbox** — no direct `KafkaTemplate.send()` in business transactions
- [ ] DLQ and replay behavior documented and config-gated
- [ ] Scheduled mutating jobs use **ShedLock** with stable lock names
- [ ] Critical aggregates (`Order`, `Payment`, `Product`) use `@Version` optimistic locking; conflicts → HTTP 409
- [ ] Outbound dependencies have timeout, retry, and circuit-breaker coverage
- [ ] Saga steps isolated with `Propagation.REQUIRES_NEW`; compensation supports safe refund + release
- [ ] No secrets in source; config environment-driven; startup fails fast on missing mandatory config
- [ ] Service docs (`docs/services/<service>/README.md`) updated when contracts or behavior change

## Service Documentation Template
1. Purpose
   - Describe the goal of the service and the business problem it solves.
2. Key Functions
   - List the main business functions.
3. Event Flows
   - Describe inbound and outbound events and any saga or choreography relationship.
4. Tech Stack
   - List frameworks, database, cache, messaging, and relevant libraries.
5. APIs / Endpoints
   - List important REST endpoints, gRPC services, or internal APIs with auth expectations.
6. Notes / Best Practices
   - Record service-specific multi-tenancy, idempotency, observability, concurrency, and limitations.
