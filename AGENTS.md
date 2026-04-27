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

## Report Rule (Mandatory)
- After every completed agent execution, write a summary to `REPORT.md` as follows:
  1. Preserve the original header content of the file.
  2. Insert the execution summary immediately below the header, describing clearly the changes made.
  3. At the bottom of the file, append a version note with:
     - Version marker (`v2`, `v3`, ...) in increasing order.
     - Timestamp of the update.
     - Short description of the changes.
- Historical entries and version notes must remain intact for traceability.

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

### Architecture Style
- External traffic enters through `gateway-service`.
- The current gateway route map in `gateway-service/src/main/resources/application.yml` fronts `user-service`, `order-service`, `file-service`, `notification-service`, `audit-log-service`, `product-service`, `payment-service`, `inventory-service`, `product-view-service`, `order-view-service`, and `dlq-replayer-service`.
- Synchronous service interaction uses REST and, where appropriate, gRPC.
- Asynchronous integration uses Kafka with canonical event envelopes from `event-contract`.
- Business events must be emitted through the transactional outbox pattern.
- Domain services own their own persistence and must not read or write each other's databases directly.
- Projection services such as `order-view-service` and `product-view-service` exist to support query-optimized read models.

### Tech Stack
- Backend: Spring Boot 4.0.6, Java 25, Spring Data JPA, Spring Kafka, Spring Cloud Gateway, gRPC, ShedLock, Micrometer, OpenTelemetry.
- Frontend: Angular 21, TypeScript 5.9, RxJS, Tailwind CSS.
- Data and infrastructure: PostgreSQL per service, Redis, Kafka, Apicurio Registry (`schema-registry` in `docker-compose.yml`), MinIO, Docker, Kubernetes, Jenkins, Prometheus, Grafana, Keycloak.
- Build system: Maven aggregator at root `pom.xml`.

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

## Engineering Standards (Mandatory)

### Naming and API Contract
- Use intention-revealing names and avoid vague abbreviations except common acronyms.
- Keep method signatures stable unless a bug fix or explicit contract change requires modification.
- Keep backward compatibility for public APIs and event payloads unless versioning is introduced deliberately.

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
- Order-centric orchestration follows a state machine such as `RESERVE_STOCK -> CHARGE_PAYMENT -> CONFIRM_STOCK -> COMPLETED` or compensation flow.
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
- Formatting baseline:
  - `semi: true`
  - `singleQuote: true`
  - `printWidth: 120`
  - `tabWidth: 2`
  - `trailingComma: all`
- `.eslintignore` and `.prettierignore` must exclude generated and non-source directories such as `node_modules`, `dist`, `build`, and `coverage`.

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
- Full stack verification: `mvn clean verify`
- Single module with dependencies: `mvn -q -pl <module-name> -am verify`
- Backend module tests: `mvn -q -pl <module-name> -am test`
- Standalone modules outside the root reactor, such as `user-service` and `file-service`, must be verified from their own directories with module-local Maven commands.
- Frontend tests: run `npm run test` inside `angular-fe`
- Frontend build: run `npm run build` inside `angular-fe`

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
- The current root `Jenkinsfile` only installs, builds, tests, containerizes, and deploys `angular-fe` (`k8s/06-angular-fe.yaml`), so backend verification remains a separate responsibility.

## Service Checklist (Mandatory Review Checklist)
- Service exposes liveness and readiness health checks.
- Service supports graceful shutdown and request draining.
- Structured logging is enabled and MDC/thread-local cleanup is performed.
- Prometheus metrics are exposed and meaningful.
- Repository queries and mutations are tenant-aware.
- Command endpoints and event consumers are idempotent where duplicate delivery is possible.
- Event publishing uses transactional outbox.
- DLQ and replay behavior are documented and configuration-gated.
- Scheduled mutating jobs use ShedLock.
- Outbound dependencies have timeout, retry, and circuit breaker coverage where appropriate.
- Critical aggregates use concurrency control.
- Documentation is updated when contracts, architecture, or operational behavior changes.
- `REPORT.md` is updated after each completed agent execution.

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
