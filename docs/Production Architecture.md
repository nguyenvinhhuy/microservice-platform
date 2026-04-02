# Production Architecture Specification

## 1. SYSTEM OVERVIEW

### Architecture Style
- Microservices with an API Gateway entry point.
- Containerized deployment model (Docker-compatible), designed for orchestration on Kubernetes.
- Event-driven architecture using Kafka for domain events, asynchronous workflows, retries, and notification processing.
- Database-per-service using PostgreSQL, plus shared Redis (cache/rate-limit state) and MinIO (object storage).

### Design Principles
- Strong service boundaries and single data ownership per domain.
- Asynchronous-first integration via immutable domain events; synchronous calls only for user-facing latency-critical validations.
- Reliability by default: transactional outbox, idempotent consumers, retry with DLQ, and deterministic event schemas.
- Security by default: Keycloak OIDC, gateway enforcement, least-privilege RBAC, and tenant isolation.
- Operable by default: metrics, logs, traces, correlation IDs, runbooks, and disaster recovery.

### System Goals
- Support millions of users through horizontal scaling and partitioned event processing.
- High availability across services and stateful stores through replication and multi-zone deployment.
- Production-grade correctness for distributed workflows using sagas, outbox, and idempotency.
- Safe evolution of APIs and events via contract governance and schema compatibility.

### Scalability Expectations
- API tier (gateway and stateless services) scales horizontally by adding replicas.
- Kafka scales via partitions, consumer groups, and topic-level throughput planning.
- PostgreSQL scales via read replicas for read-model services and sharding by tenant when necessary.
- Redis scales via clustering and dedicated instances for rate limiting vs caching.
- MinIO scales via erasure coding and multi-site replication for critical buckets.

### High-Level Component Diagram Explanation (Textual)
- Clients use `angular-fe` and authenticate against Keycloak.
- Requests enter `gateway-service`, where JWT is validated, rate limiting is applied, correlation IDs are set, and routing occurs.
- Gateway routes to domain microservices (`user-service`, `order-service`, `file-service`, `notification-service`, `audit-log-service`).
- Domain microservices own their PostgreSQL databases and publish domain events to Kafka using a transactional outbox.
- Projection services (CQRS read side) consume Kafka events and build denormalized read models for fast queries.
- Kafka is used for asynchronous tasks, notification fan-out, retries, and dead-letter processing through topic-based workflows.
- Observability stack (Prometheus, Grafana) monitors system health; centralized logging and tracing are added for production operations.

## 2. ARCHITECTURE LAYERS

### Frontend Layer
- Component: `angular-fe`.
- Responsibilities:
  - User interaction and session initiation via Keycloak login.
  - Calls gateway APIs only; no direct calls to microservices.
- Non-functional requirements:
  - Enforce HTTPS-only in production.
  - Use short-lived access tokens and refresh tokens (handled by Keycloak/OIDC client).

### API Gateway Layer
- Component: `gateway-service`.
- Responsibilities:
  - JWT validation and authorization enforcement.
  - Routing and request shaping (timeouts, payload limits).
  - Request correlation (`X-Correlation-Id`) and trace propagation.
  - Rate limiting using Redis.
  - Multi-tenant context extraction and propagation (`X-Tenant-Id`).

### Security Layer
- Components: `keycloak`, `keycloak-db`.
- Responsibilities:
  - Identity provider (OIDC).
  - Token issuance and refresh.
  - Realm/client configuration for web and service access.

### Domain Microservices Layer
- Components: `user-service`, `order-service`, `file-service`, `notification-service`, `audit-log-service`.
- Responsibilities:
  - Implement business domains with strict data ownership.
  - Publish domain events for state changes.
  - Consume relevant events to update local state or trigger workflows.

### Event Backbone
- Components: `kafka`, `zookeeper` (or KRaft in the production target if upgrading Kafka mode).
- Responsibilities:
  - Durable event log for domain events and integration events.
  - Retry and DLQ topics for consumer failure management.

### Data Layer
- Components: PostgreSQL database per service.
- Responsibilities:
  - Source of truth for each domain service.
  - Transactional outbox co-located with domain data for atomic publish.

### Infrastructure Layer
- Components: `redis`, `kafka`, `minio`, per-service Postgres, container network.
- Responsibilities:
  - Caching and rate limiting (Redis).
  - Event streaming, retries, and notification delivery workflows (Kafka).
  - Object storage (MinIO).

### Observability Layer
- Existing: `prometheus`, `grafana`.
- Production upgrades:
  - Centralized logs (ELK/OpenSearch stack).
  - Distributed tracing (OpenTelemetry collector + Jaeger/Tempo).
  - Alerting (Prometheus Alertmanager).

### DevOps Layer
- Existing: `jenkins`, `docker-registry`.
- Production upgrades:
  - Git-based pipelines with staged environments.
  - Immutable images and promotion strategy.
  - GitOps deployment (Argo CD or equivalent) or Jenkins-driven deploy with audit trails.

## 3. DOMAIN SERVICE ARCHITECTURE

This section defines target domain boundaries and event contracts for production. Event names and ownership are specific to the listed services and are required for cross-service integration.

### Service: user-service
Responsibilities:
- User profile and account lifecycle.
- Role assignments and tenant membership mapping (authorization source of truth for app-level roles; identity remains in Keycloak).
Database: `user-db` (owned by user-service).
Produces events:
- `user.created.v1`
- `user.updated.v1`
- `user.role.assigned.v1`
- `user.tenant.membership.updated.v1`
Consumes events:
- Not defined in architecture.md. (Target production: consumes `order.created.v1` only if user-level analytics or counters are needed.)

### Service: order-service
Responsibilities:
- Order lifecycle management (create, confirm, cancel).
- Orchestrates distributed workflows via saga patterns (inventory/payment equivalents are integration points, not necessarily separate services in this platform scope).
Database: `order-db` (owned by order-service).
Produces events:
- `order.created.v1`
- `order.confirmed.v1`
- `order.cancelled.v1`
- `order.status.changed.v1`
Consumes events:
- Not defined in architecture.md. (Target production: consumes `payment.completed.v1`, `payment.failed.v1`, `inventory.reserved.v1`, `inventory.released.v1` if those domains exist.)

### Service: file-service
Responsibilities:
- File upload, metadata management, and retrieval for domain attachments.
- Generates pre-signed URLs and maintains file metadata integrity.
Database: `file-db` (owned by file-service).
Object storage: MinIO bucket(s) owned by file-service (bucket naming and access policy defined in Data Architecture).
Produces events:
- `file.created.v1`
- `file.deleted.v1`
- `file.virus.scanned.v1` (if malware scanning is deployed).
Consumes events:
- Not defined in architecture.md. (Target production: consumes `user.deleted.v1` or `order.cancelled.v1` to apply retention policies if required.)

### Service: notification-service
Responsibilities:
- Notification orchestration (email/SMS/push/webhook) and delivery status tracking.
- Consumes domain events and schedules notification tasks.
Database: `notification-db` (owned by notification-service).
Produces events:
- `notification.requested.v1`
- `notification.sent.v1`
- `notification.failed.v1`
Consumes events:
- Target production: consumes `order.created.v1`, `order.confirmed.v1`, `order.cancelled.v1`, `user.created.v1`.
- Kafka topics are used for delivery tasks, retries, and dead-letter handling.

### Service: audit-log-service
Responsibilities:
- Immutable audit log ingestion and query for security and compliance use cases.
- Stores append-only audit records derived from domain events and gateway security events.
Database: `audit-db` (owned by audit-log-service).
Produces events:
- `audit.recorded.v1` (optional, if external SIEM integration is required).
Consumes events:
- Target production: consumes a broad set of domain events (`*.v1`) and gateway security events (`auth.*.v1`) via Kafka for centralized audit trails.

### Service: gateway-service
Responsibilities:
- API entry point and policy enforcement.
- Produces operational and security events for audit.
Database: `gateway-db` (if used for gateway-specific persistence such as API keys, allow-lists, or request logs; exact usage is not defined in architecture.md).
Produces events:
- `auth.login.succeeded.v1`
- `auth.login.failed.v1`
- `security.rate_limited.v1`
- `api.request.rejected.v1`
Consumes events:
- Not defined in architecture.md. (Target production: typically none.)

## 4. CQRS ARCHITECTURE

### Why CQRS Is Used
- Separation of concerns between transactional write models and optimized query models.
- Independent scaling: write services scale by CPU/IO; read services scale by query volume.
- Reduced coupling: read-side can evolve without impacting write-side transactions.

### Write Services (Source of Truth)
- `user-service` (writes to `user-db`).
- `order-service` (writes to `order-db`).
- `file-service` (writes to `file-db` and MinIO objects; metadata is the transactional record).
- `notification-service` (writes to `notification-db`).
- `audit-log-service` (writes to `audit-db`).

### Read Services (Projection Services)
The platform adds read-side services that consume Kafka events and build denormalized tables.

- `user-view-service`
  - Read model DB: `user_view_db` (or schema in a dedicated Postgres instance).
  - Read APIs:
    - `GET /views/users`
    - `GET /views/users/{id}`

- `order-view-service`
  - Read model DB: `order_view_db`.
  - Read APIs:
    - `GET /views/orders`
    - `GET /views/orders/{id}`

- `audit-view-service` (optional if audit querying is heavy)
  - Read model DB: `audit_view_db`.
  - Read APIs:
    - `GET /views/audit`

Projection build rules:
- Projections consume only Kafka events, never query write databases of other services.
- Projection handlers must be idempotent.
- Projection updates must be monotonic by `(aggregateId, aggregateVersion)` to prevent out-of-order writes.

## 5. EVENT-DRIVEN ARCHITECTURE

### Kafka Topic Naming Conventions
- Primary topic per service: `<service>.events`
- Retry topic per service: `<service>.events.retry`
- Dead-letter topic per service: `<service>.events.dlq`
- For high-volume aggregates, allow dedicated topics: `<service>.<aggregate>.events`

Examples:
- `user.events`
- `order.events`
- `file.events`
- `notification.events`
- `audit.events`

### Event Producers
- `user-service` publishes to `user.events`.
- `order-service` publishes to `order.events`.
- `file-service` publishes to `file.events`.
- `notification-service` publishes to `notification.events`.
- `audit-log-service` publishes to `audit.events` (optional) and consumes broadly.

### Event Consumers
- Projection services (`*-view-service`) consume from the relevant `<service>.events`.
- `notification-service` consumes `user.events` and `order.events` to trigger notifications.
- `audit-log-service` consumes all domain events to build an audit trail.

### Standard Event Envelope (Required)
All Kafka events use this JSON envelope.

```json
{
  "eventId": "ULID",
  "eventType": "domain.event",
  "source": "service-name",
  "eventTime": "ISO8601",
  "aggregateId": "entity-id",
  "aggregateVersion": 1,
  "dataSchema": "event.schema.v1",
  "traceId": "trace-id",
  "correlationId": "workflow-id",
  "causationId": "event-id",
  "tenantId": "tenant-id",
  "data": {}
}
```

Envelope rules:
- `eventId` is globally unique and stable across retries (ULID recommended for time-sort).
- `aggregateVersion` increments per aggregate instance in the producing service.
- `traceId` and `correlationId` are mandatory for end-to-end debugging.
- `tenantId` is mandatory for multi-tenant isolation and observability.

### Event Versioning Strategy
- Event type includes a version suffix: `order.created.v1`.
- The `dataSchema` points to a schema registry artifact version.
- Producers are backward compatible for consumers by only adding optional fields.

### Schema Evolution
- Backward compatibility is required for all changes in `v1` streams.
- Breaking changes require a new event type major version (`*.v2`) and parallel consumption during migration.

## 6. EVENT GOVERNANCE

### Schema Registry
- Use Apicurio Schema Registry or Confluent Schema Registry as the authoritative store of event schemas.
- Every event type must have a registered schema referenced by `dataSchema`.

### Compatibility Rules
- Default rule: BACKWARD compatibility for existing consumers.
- Allowed changes:
  - Add optional fields.
  - Widen enum values when consumer handles unknowns safely.
- Disallowed changes without new major version:
  - Removing required fields.
  - Changing field semantics or types.

### Contract Evolution Process
- Schema change requires:
  - Updating schema in registry.
  - Updating producer with validation before publish.
  - Updating consumers with tolerant parsing and unknown-field ignore.
- CI pipeline blocks release if:
  - Schema compatibility checks fail.
  - Producer publishes unregistered schema identifiers.

## 7. RELIABILITY PATTERNS

### Transactional Outbox (Kafka)
Implementation requirements:
- Each write service maintains an outbox table in its own Postgres database.
- State change and outbox insert happen in the same DB transaction.
- A publisher job reads pending outbox rows and publishes to Kafka, then marks rows as published.

Outbox schema (example):
- `outbox(event_id, aggregate_id, aggregate_version, event_type, tenant_id, payload_json, created_at, status, published_at, retry_count)`

Operational requirements:
- Publisher is protected with a distributed lock for scheduled jobs (ShedLock) if the service runs with multiple replicas.
- Publisher must be idempotent and safe on restart.

### Consumer Idempotency
Implementation requirements:
- Each consuming service persists processed event IDs in `processed_events(event_id, processed_at)` within its own database.
- Consumer checks `processed_events` before applying side effects.
- Exactly-once business effects are achieved by idempotent processing on top of at-least-once delivery.

### Retry Policy
Kafka consumer retry:
- Use retry topics (`<service>.events.retry`) with exponential backoff.
- After max attempts, route to DLQ topic (`<service>.events.dlq`) with failure metadata.

Kafka task retry:
- Use retry topics, delayed reprocessing, and DLQ topics for notification delivery tasks.

### Dead Letter Queue (DLQ)
DLQ requirements:
- DLQ events must be persisted and searchable for operations.
- Provide admin tooling to reprocess or skip DLQ records with audit logs of operator actions.

## 8. RESILIENCE LAYER

All synchronous service-to-service calls and external dependencies must be protected.

### Circuit Breaker
Applies to:
- Calls from `gateway-service` to downstream microservices.
- Any synchronous calls from domain services to other services (only when required).
Rules:
- Fail fast during downstream outages.
- Emit metrics and audit events on open/half-open transitions.

### Retry
Applies to:
- Transient network failures when calling downstream services.
Rules:
- Bounded retries with jitter.
- Do not retry on 4xx business errors.

### Timeout
Applies to:
- All outbound HTTP calls.
Rules:
- Enforce low timeouts at gateway to protect thread pools.
- Use separate timeouts for connect/read.

### Bulkhead
Applies to:
- Isolation of thread pools for different downstream targets.
Rules:
- Prevent one dependency from exhausting resources for all calls.

Implementation note:
- Resilience4j policies are standardized across services with consistent instance naming and Prometheus metrics exposure.

## 9. SECURITY ARCHITECTURE

### Authentication (Keycloak)
- Keycloak provides OIDC login for `angular-fe`.
- `gateway-service` validates JWTs for all protected APIs.

### JWT Validation at Gateway
Gateway must:
- Validate token signature and issuer.
- Validate audience/client ID.
- Enforce token expiration and clock skew.
- Map claims to application principals and roles.

### Identity Propagation to Services
Gateway forwards identity context to services via headers for convenience, but services must treat them as derived from JWT validation.

Required headers:
- `X-User-Id`
- `X-Roles`
- `X-Tenant-Id`
- `X-Correlation-Id`
- `traceparent` (W3C trace propagation)

### RBAC Model
Role model requirements:
- Realm roles for platform-wide roles (admin, support).
- Client roles for application roles (order.read, order.write, user.admin).
- Services enforce authorization using roles and tenant context.

### Multi-Tenant Isolation
Tenant model requirements:
- Every request must include resolved `tenantId` from token claims.
- Every write service enforces tenant ownership checks before mutation.
- Kafka events include `tenantId` in the envelope and are processed with tenant-aware projections.
- Database schema:
  - Minimum: include `tenant_id` column on tenant-scoped tables with indexed access patterns.
  - Advanced: optional schema-per-tenant or database-per-tenant for high isolation tiers (explicitly a scaling option).

## 10. API GATEWAY DESIGN

### Responsibilities of gateway-service
- Routing strategy:
  - Route by path prefix: `/api/users/**` -> `user-service`, `/api/orders/**` -> `order-service`, `/api/files/**` -> `file-service`.
  - Route read APIs to view services under `/api/views/**` when CQRS is enabled.
- Authentication enforcement:
  - Require JWT for protected routes.
  - Allow anonymous access only for explicitly whitelisted endpoints (health checks, public assets).
- Rate limiting:
  - Redis-backed token bucket per `(tenantId, userId, routeKey)`.
  - Separate limits for public vs authenticated APIs.
- Request correlation:
  - Generate `X-Correlation-Id` if missing.
  - Propagate correlation and trace headers downstream.

### Integration with Keycloak
- OIDC client configuration for SPA (PKCE).
- Gateway configured as resource server to validate JWTs.

### Integration with Redis Rate Limiting
- Redis keys include tenant scope and route scope.
- Redis is isolated into a dedicated logical database or cluster for rate limiting to avoid eviction risks from caching.

## 11. DATA ARCHITECTURE

### Database-Per-Service
- Each service owns its Postgres database and schema.
- Cross-service reads are forbidden in request paths; integrate via events and projections.

### Migration Strategy
- Use Flyway or Liquibase in every service for deterministic schema evolution.
- Migration rules:
  - Backward compatible schema changes before deploy.
  - Roll-forward only; emergency rollback is handled by forward migrations.

### Redis Usage Rules
- Use Redis for:
  - Rate limiting state.
  - Short-lived caches where stale reads are acceptable.
  - Session-related ephemeral data if needed.
- Do not use Redis as the system of record for business data.
- Use explicit key prefixes: `<service>:<tenantId>:<purpose>:<id>`.

### MinIO Usage Rules
- File objects are stored in MinIO; metadata stored in `file-db`.
- Buckets are owned by `file-service` and accessed through pre-signed URLs.
- Enforce retention policies and encryption-at-rest where supported.
- Multi-tenant separation:
  - Prefix objects by tenant: `<tenantId>/<domain>/<objectId>`.

## 12. OBSERVABILITY

### Metrics (Prometheus)
- Each service exposes `/actuator/prometheus` (or equivalent) with:
  - HTTP request latency (p95/p99).
  - Error rates.
  - JVM metrics.
  - Kafka consumer lag and processing latency.
  - Outbox backlog size and oldest event age.
  - Rate limiting decisions at gateway.

### Dashboards (Grafana)
- Required dashboards:
  - Gateway overview (traffic, latency, 4xx/5xx, rate limiting).
  - Service golden signals per microservice.
  - Kafka (consumer lag, throughput, DLQ rates).
  - Outbox and event processing health.
  - Postgres health (connections, slow queries).

### Centralized Logging (ELK/OpenSearch)
- Collect structured JSON logs.
- Required log fields:
  - `service`, `tenantId`, `userId`, `traceId`, `spanId`, `correlationId`, `eventId`, `orderId` (when applicable).

### Distributed Tracing (OpenTelemetry)
- Use OpenTelemetry SDK in gateway and services.
- Export to an OpenTelemetry collector, then to Jaeger/Tempo.
- Trace propagation:
  - HTTP uses `traceparent`.
  - Kafka propagates trace context in message headers.
- Every event envelope includes `traceId` for cross-tool correlation.

## 13. OPERATIONAL RESILIENCE

### Backup Strategy
- PostgreSQL:
  - Nightly full backups + WAL archiving for point-in-time recovery.
  - Regular restore drills.
- Kafka:
  - Multi-broker replication with appropriate replication factor and min in-sync replicas.
  - Mirror critical topics across regions if required.
- Redis:
  - Enable persistence (AOF) for rate limiting state if loss is unacceptable, otherwise accept rebuild.
  - Use replicas and automatic failover.
- MinIO:
  - Enable bucket replication for critical buckets.
  - Verify object integrity and lifecycle policies.

### Disaster Recovery (DR)
- Define RPO/RTO targets per domain:
  - Orders: low RPO, moderate RTO.
  - Audit logs: low RPO, higher RTO acceptable if append-only.
- Runbook requirements:
  - Region failover steps.
  - Kafka topic recovery steps.
  - Database restore procedures and verification checks.

## 14. DEVOPS AND CI/CD

### Jenkins Pipelines
- Stages per service:
  - Build and unit tests.
  - Contract tests (API and event schema compatibility).
  - Container build.
  - Security scans (dependency and image scanning).
  - Push to `docker-registry`.
  - Deploy to staging, run smoke tests, then promote to production.

### Docker Image Build
- Use multi-stage builds.
- Pin base images by digest.
- Non-root runtime user.

### Docker Registry Usage
- Immutable tags per commit SHA.
- Promote images across environments by tag aliasing or manifest promotion.

### Deployment Strategy
- Kubernetes target with rolling updates.
- Blue/green or canary for gateway and high-risk services.
- Database migrations executed before traffic shift, with backward compatible changes.

## 15. SCALABILITY STRATEGY

### Horizontal Scaling
- All API-serving services are stateless and scale by replicas.
- Store state only in Postgres, Kafka, Redis, MinIO.

### Autoscaling
- HPA based on CPU, memory, and custom metrics:
  - Gateway: requests/sec, p95 latency.
  - Consumers: Kafka lag.

### Partitioning Strategy
- Kafka partitions sized by throughput and consumer parallelism.
- Key messages by `aggregateId` to preserve per-aggregate ordering.
- If multi-tenant load is skewed, consider partitioning by `(tenantId, aggregateId)` with careful ordering rules.
