# CLAUDE.md — microservice-platform

Full engineering standards (naming, JavaDoc, idempotency, saga, outbox, concurrency, Git, PR rules) live in **`AGENTS.md`**. This file gives Claude Code the concrete, reference-level knowledge needed to work productively across every service without re-reading everything from scratch.

---

## Table of Contents

1. [Quick Commands](#1-quick-commands)
2. [Module Map & Ports](#2-module-map--ports)
3. [Infrastructure Services](#3-infrastructure-services)
4. [Architecture Overview](#4-architecture-overview)
5. [Gateway Routes](#5-gateway-routes)
6. [REST API Reference](#6-rest-api-reference)
7. [Event Contract Reference](#7-event-contract-reference)
8. [Kafka Topics](#8-kafka-topics)
9. [Database Schemas](#9-database-schemas)
10. [Order Saga Flow](#10-order-saga-flow)
11. [Security & Auth](#11-security--auth)
12. [Key Configuration Classes](#12-key-configuration-classes)
13. [Java Formatting](#13-java-formatting)
14. [Environment Variables](#14-environment-variables)
15. [Service Checklist](#15-service-checklist)
16. [Common Patterns](#16-common-patterns)

---

## 1. Quick Commands

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
# Production arch:     docs/Production\ Architecture.md
```

---

## 2. Module Map & Ports

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

---

## 3. Infrastructure Services

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

---

## 4. Architecture Overview

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

**Key patterns:**
- All domain events go through the **transactional outbox** — never direct `KafkaTemplate.send()` in transactional business code.
- All Kafka consumers check `processed_events(event_id, consumer_service)` via `JdbcIdempotencyService` to prevent duplicate processing.
- Projection services (`*-view-service`) are read-only and built from event streams.
- Failed messages → `<topic>-dlq`, replayed via `dlq-replayer-service` (configuration-gated, opt-in only).

---

## 5. Gateway Routes

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

---

## 6. REST API Reference

### 6.1 Order Service — `:8002`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `POST` | `/api/orders` | **required** | Creates order, starts saga (RESERVE_STOCK) |
| `POST` | `/api/orders/{orderId}/pay` | **required** | Advances saga to CHARGE_PAYMENT |
| `POST` | `/api/orders/{orderId}/cancel` | **required** | Cancels order, triggers compensation |

### 6.2 User Service — `:8001`

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

### 6.3 Payment Service — `:8007`

| Method | Path | Idempotency-Key | Notes |
|---|---|---|---|
| `POST` | `/api/payments` | **required** | Process payment — already enforces `Idempotency-Key` |
| `GET` | `/api/payments/{paymentId}` | — | Get payment by ID |

### 6.4 Product Service — `:8006` (roles: `ROLE_ADMIN`, `ROLE_VIEWER`)

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

### 6.5 Inventory Service — `:8008` (internal only)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/inventory/internal/reservations` | Reserve stock |
| `POST` | `/api/inventory/internal/reservations/{orderId}/confirm` | Confirm reservation |
| `POST` | `/api/inventory/internal/reservations/{orderId}/release` | Release reservation |

### 6.6 File Service — `:8003`

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

### 6.7 Notification Service — `:8004`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/notifications` | List notification history (`limit` query param) |
| `GET` | `/api/notifications/preferences` | Get notification preferences |

### 6.8 Audit Log Service — `:8005` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/audit-logs` | List logs (filters: `eventType`, `aggregateId`, `page`, `size`) |
| `GET` | `/api/audit-logs/{id}` | Get log by ID |
| `GET` | `/api/audit-logs/user/{userId}` | Logs for a user |
| `GET` | `/api/audit-logs/search` | Search (eventType, aggregateId, page, size) |

### 6.9 Order View Service — `:8011` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/views/orders` | List order views (optional `X-User-Id` header, page, size) |
| `GET` | `/api/views/orders/{id}` | Get order view |

### 6.10 Product View Service — `:8010` (header: `X-Tenant-Id`)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/views/products` | List product views (page, size) |
| `GET` | `/api/views/products/{id}` | Get product view |

### 6.11 DLQ Replayer Service — `:8012`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/admin/dlq/events` | List DLQ events (status filter, page, size) |
| `POST` | `/api/admin/dlq/replay` | Replay event (body: `{id, overrideTopic?}`) |
| `POST` | `/api/admin/dlq/skip` | Skip event (body: `{id}`) |

---

## 7. Event Contract Reference

All events extend `BaseEvent<T>` from `event-contract`. The envelope fields are:

```
eventId:String, eventType:String, source:String, eventTime:Instant,
aggregateId:String, aggregateVersion:long, dataSchema:String,
traceId:String, correlationId:String, causationId:String, data:T
```

JSON schemas live in `event-contract/src/main/resources/schemas/` and are validated at deserialization time.

### Order Events (`order.events`)

| Class | Key Fields |
|---|---|
| `OrderCreatedEvent` | orderId, tenantId, userId, status, totalAmount, currency, timestamp |
| `OrderPaidEvent` | orderId, tenantId, userId, status, paymentId, timestamp |
| `OrderCancelledEvent` | orderId, tenantId, userId, status, timestamp |
| `OrderFailedEvent` | orderId, tenantId, userId, status, reason, timestamp |

### Payment Events (`payment.events`)

| Class | Key Fields |
|---|---|
| `PaymentProcessingEvent` | orderId, paymentId, tenantId |
| `PaymentCompletedEvent` | orderId, paymentId, tenantId, transactionId |
| `PaymentFailedEvent` | orderId, paymentId, tenantId, reason |

### Inventory Events (`inventory.events`)

| Class | Key Fields |
|---|---|
| `StockReservedEvent` | orderId, tenantId, amount, currency, paymentProvider, idempotencyKey, items[]{productId, quantity} |
| `StockConfirmedEvent` | orderId, tenantId, items[]{productId, quantity} |
| `StockReleasedEvent` | orderId, tenantId, items[]{productId, quantity} |
| `StockReservationFailedEvent` | orderId, tenantId, reason |
| `StockUpdatedEvent` | tenantId, productId, totalStock, reservedStock, availableStock |

### Product Events (`product.events`)

| Class | Key Fields |
|---|---|
| `ProductUpdatedEvent` | tenantId, productId, code, name, price, currency |
| `ProductPriceUpdatedEvent` | tenantId, productId, price, currency |

### User Events (`user.events`)

| Class | Key Fields |
|---|---|
| `UserCreatedEvent` | userId, keycloakUserId, tenantId, email, fullName, phoneNumber, avatarUrl, status, locale, timezone, createdAt |
| `UserUpdatedEvent` | userId, keycloakUserId, tenantId, email, fullName, phoneNumber, avatarUrl, status, locale, timezone, updatedAt |
| `UserPreferencesUpdatedEvent` | userId, tenantId, emailEnabled, smsEnabled, pushEnabled, marketingEnabled, language, updatedAt |
| `UserAddressCreatedEvent` | userId, tenantId, addressId, label, country, city, district, addressLine, postalCode, isDefault, createdAt |

### File Events (`file.events`)

| Class | Key Fields |
|---|---|
| `FileUploadedEvent` | fileId, tenantId, ownerUserId, category, bucket, objectKey, originalFilename, contentType, sizeBytes, checksumSha256, visibility, uploadedAt |
| `FileAvailableEvent` | fileId, tenantId, ownerUserId, category, bucket, objectKey, contentType, sizeBytes, checksumSha256, visibility, availableAt |
| `FileDeletedEvent` | fileId, tenantId, ownerUserId, bucket, objectKey, deletedAt |
| `FileQuarantinedEvent` | fileId, tenantId, ownerUserId, malwareStatus, reason, quarantinedAt |
| `FileScanCompletedEvent` | fileId, tenantId, malwareStatus, reason, scanDurationMs, scannerName, timedOut, checksumBlacklisted, scannedAt |

---

## 8. Kafka Topics

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

---

## 9. Database Schemas

### order-service — `order_db` (port 5436)

| Table | Purpose |
|---|---|
| `orders` | id, tenant_id, user_id, status, total_amount, currency, created_at, updated_at, **version** |
| `order_items` | order_id(FK), product_id, quantity, price_at_purchase |
| `order_payments` | order_id(PK), payment_id, provider, status, amount, created_at, updated_at |
| `idempotency_keys` | id, tenant_id, action, request_id, order_id, created_at |
| `order_sagas` | id, tenant_id, order_id, state, payment_provider, payment_id, request_id, retry_count, last_error, created_at, updated_at, **version** |
| `kafka_outbox` | id, topic, message_key, payload, headers_json, status, created_at, published_at, attempt_count |
| `processed_events` | event_id, consumer_service, processed_at |

### user-service — `user_db` (port 5435)

| Table | Purpose |
|---|---|
| `users` | id, keycloak_user_id, tenant_id, email, full_name, phone_number, avatar_url, status, locale, timezone, created_at, updated_at, deleted_at, **version** |
| `user_preferences` | id, tenant_id, user_id, email/sms/push/marketing_enabled, language, **version** |
| `user_addresses` | id, tenant_id, user_id, label, country, city, district, address_line, postal_code, is_default, **version** |
| `user_memberships` | id, tenant_id, user_id, role, status, created_at |
| `kafka_outbox` | — |
| `processed_events` | — |

### product-service — `product_db` (port 5440)

| Table | Purpose |
|---|---|
| `t_products` | id(BIGSERIAL), code(UNIQUE), name, slug(UNIQUE), short_description, description, brand, category_id, price, currency, status, thumbnail_url, rating_average, rating_count, tenant_id |
| `t_product_images` | product_id(FK), url, is_primary, sort_order |
| `t_product_attributes` | product_id(FK), name, value |
| `t_product_prices` | product_id(FK), price, currency, valid_from, valid_to |
| `product_outbox` | — |
| `shedlock` | — |
| `processed_events` | — |

### inventory-service — `inventory_db` (port 5442)

| Table | Purpose |
|---|---|
| `inventory` | id(BIGSERIAL), product_id, total_stock, reserved_stock, tenant_id, **version** — UNIQUE(tenant_id, product_id) |
| `inventory_reservation` | reservation_id(UUID), order_id, tenant_id, status, expires_at, amount, currency, payment_provider, idempotency_key, correlation_id, trace_id |
| `inventory_reservation_item` | reservation_id(FK), product_id, quantity |
| `outbox_events` | event_id(UUID UNIQUE), event_type, partition_key, payload, status, retry_count, next_attempt_at, published_at, last_error |
| `shedlock` | — |

### payment-service — `payment_db` (port 5441)

| Table | Purpose |
|---|---|
| `payments` | payment_id(UUID PK), order_id, amount, currency, status, provider, transaction_id, idempotency_key(UNIQUE), created_at, updated_at, **version** |
| `payment_outbox` | aggregate_type, aggregate_id, event_type, payload, published, publish_attempts, next_attempt_at, last_error |
| `processed_events` | event_id(UUID), consumer_service, processed_at — **Note**: `event_id` is PK here, `consumer_service` is metadata only |
| `shedlock` | — |

> **Payment exception**: `processed_events` uses `event_id` as PK (not composite). Inspect schema before extending payment consumers.

### file-service — `file_db` (port 5437)

| Table | Purpose |
|---|---|
| `files` | id(UUID), tenant_id, owner_user_id, category, object_key(UNIQUE), bucket, original_filename, content_type, size_bytes, checksum_sha256, storage_provider, status, visibility, malware_scan_status, metadata_json, **version** |
| `file_access_audit` | file_id, actor_user_id, action, outcome, details |
| `file_quota` | tenant_id(PK), used_bytes, quota_bytes, **version** |
| `api_idempotency` | tenant_id, idempotency_key, request_path, request_hash, status, response_body, expires_at |
| `multipart_upload_sessions` | file_id, upload_id, status, initiated_at, expires_at |

### notification-service — `notification_db` (port 5438)

| Table | Purpose |
|---|---|
| `notification_history` | tenant_id, user_id, event_type, status, created_at |
| `notification_preferences` | tenant_id, user_id, email/sms/push_enabled |
| `kafka_outbox` | UUID PK, topic, message_key, payload, claim_status, publish_status |
| `api_idempotency` | tenant_id, idempotency_key, request_path, status, response_body, expires_at |
| `shedlock` | — |

### audit-log-service — `audit_db` (port 5439)

| Table | Purpose |
|---|---|
| `audit_log` | id(BIGSERIAL), event_id(VARCHAR 64, UNIQUE), event_type, source, tenant_id, user_id, aggregate_id, aggregate_type, correlation_id, causation_id, raw_payload, received_at |
| `processed_events` | — |

### order-view-service — `order_view_db` (port 5444)

| Table | Purpose |
|---|---|
| `order_view` | (tenant_id + order_id) PK, user_id, status, payment_status, stock_status, total_price, created_at |

### product-view-service — `product_view_db` (port 5443)

| Table | Purpose |
|---|---|
| `product_view` | (tenant_id + product_id) PK, name, price, stock, status, updated_at |

### dlq-replayer-service — `dlq_replayer_db` (port 5445)

| Table | Purpose |
|---|---|
| `dlq_events` | id(BIGSERIAL), topic, partition, offset, message_key, payload, status, original_topic, created_at |
| `processed_events` | — |

---

## 10. Order Saga Flow

The order saga is the primary distributed workflow in this platform. It lives in `order-service`.

### States

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

### Persistence (`order_sagas` table)

```
tenant_id, order_id (UNIQUE together — one saga per order)
state, payment_provider, payment_id, request_id (idempotency)
retry_count, last_error, version (optimistic lock)
```

### Crash Recovery

- Scheduled task (`@Scheduled fixedDelay=5s`) resumes non-terminal sagas: `RESERVE_STOCK`, `CHARGE_PAYMENT`, `CONFIRM_STOCK`, `COMPENSATING`.
- Uses **ShedLock** (`saga-resume-lock`) to prevent concurrent execution across replicas.
- Each step is wrapped in `@Transactional(propagation = Propagation.REQUIRES_NEW)` for isolation.
- State + persisted `paymentId` make every step safe to re-execute — all downstream calls are idempotent.

### Trigger Flows

- `POST /api/orders` → create order → saga starts at `RESERVE_STOCK`
- `POST /api/orders/{id}/pay` → user triggers `CHARGE_PAYMENT`
- `POST /api/orders/{id}/cancel` → releases stock if state allows → order `CANCELLED`

---

## 11. Security & Auth

### Gateway

- **OAuth2 Resource Server** with JWT validation (Keycloak as issuer).
- `/api/admin/**` requires `ROLE_ADMIN`.
- All other `/api/**` require authentication.
- JWT authority mapping: Keycloak realm roles → Spring `ROLE_` prefix.
- CORS: `gateway.cors.allowed-origins` env var.

### Service-Level Auth

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

### Allowed Internal Callers (via `azp` JWT claim)

```
user-service.security.internal.allowed-authorized-parties:
  gateway-service, order-service, payment-service, inventory-service,
  product-service, notification-service, audit-log-service,
  file-service, order-view-service, product-view-service, dlq-replayer-service

file-service.security.internal.allowed-authorized-parties:
  gateway-service, notification-service
```

### Idempotency Header Summary

| Service | Header | Scope |
|---|---|---|
| order-service | `Idempotency-Key` | create, pay, cancel |
| user-service | `Idempotency-Key` | PUT me, PUT preferences, POST addresses |
| payment-service | `Idempotency-Key` | POST /api/payments (mandatory, stored in `payments.idempotency_key`) |
| file-service | `Idempotency-Key` | multipart initiate/complete/abort, confirm, delete |
| product-service | `Idempotency-Key` | POST (optional) |

> `X-Request-Id` is the transport-level tracing header — do **not** treat it as a business idempotency key for new work.

---

## 12. Key Configuration Classes

### Shared (`event-infra`)

| Class | Purpose |
|---|---|
| `IdempotencyConfig` | `JdbcIdempotencyService` bean — `alreadyProcessed()` + `markProcessed()` |
| `KafkaConsumerConfig` | Centralized consumer group, deserialization, error handling |
| `TracingConfig` | OpenTelemetry auto-configuration + custom spans |
| `ShedLockConfig` | Distributed lock provider (JDBC-backed) |
| `WorkerExecutorConfig` | Thread pools for event workers |
| `ResilienceExecutorsConfig` | Circuit breaker, retry, bulkhead thread pools |
| `SchedulingConfig` | Task scheduling baseline |

### order-service

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

### user-service

| Class | Purpose |
|---|---|
| `CacheConfig` | Redis cache manager |
| `SecurityConfig` | JWT + `InternalAccessEvaluator` |

### file-service

| Class | Purpose |
|---|---|
| `FileServiceConfig` | File lifecycle policy |
| `StorageConfig` | MinIO / S3 client |
| `CacheConfig` | Redis cache for presigned URLs + metadata |
| `SecurityConfig` | JWT + `InternalAccessEvaluator` |

### product-service / inventory-service

| Class | Purpose |
|---|---|
| `KafkaTopicConfig` | Topic name constants |
| `SchemaRegistryConfig` | Schema registry client |
| `ShedLockConfig` | Scheduled job locking |
| `SecurityConfig` | `UserContextFilter` |

---

## 13. Java Formatting

| Scope | Tool | How to run |
|---|---|---|
| Reactor modules (all except user-service, file-service) | **Spotless + Google Java Format** | `mvn spotless:apply` (or `mvn verify` — CI enforces it) |
| Standalone modules (user-service, file-service) | Prettier + `prettier-plugin-java` | IDE on-save, or `npx prettier --write "**/*.java"` from repo root |
| Frontend / TS / JSON / YAML / Markdown | Prettier (root `.prettierrc`) | `npx prettier --write .` |

`.prettierrc` settings: `semi:true`, `singleQuote:true`, `printWidth:120`, `tabWidth:2` (Java override: `tabWidth:4`), `trailingComma:all`, `parser:angular` for HTML.

When Spotless and prettier-plugin-java disagree on output for reactor modules, **Spotless/Google Java Format wins** — it is what CI checks.

---

## 14. Environment Variables

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

---

## 15. Service Checklist

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

---

## 16. Common Patterns

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
