# Engineering Conventions (Principal Standard)

## Scope
- Applies to all code generated or modified by agents in this repository.
- Preserve existing project structure and package layout.
- Keep changes minimal, deterministic, and production-safe.

## Java Method Comment Rule (Mandatory)
- Every non-trivial method must use JavaDoc block format directly above the method.
- If a method/constructor has annotations, the JavaDoc block must be placed above the first annotation (i.e., JavaDoc must be the top-most block for that method/constructor).
- The block must match this exact structure and placeholders must be replaced with meaningful text:
```java
/**
 * Describes the purpose of the method and the action it performs.
 *
 * @param <paramName> Description of the parameter.
 * @param <paramName> Description of another parameter (if applicable).
 * @return Description of the return value or side effects (even for void methods).
 */
```
- Do not keep placeholder tokens like `<short description>`, `<paramName>`, or `<short meaning>` in committed code.
- If method is `void`, `@return` is still mandatory and must describe side effects.
- Exactly one blank JavaDoc line (` *`) is required between description and first tag.
- Description line must be a complete sentence and must end with a period.
- `@param` and `@return` lines must be complete sentences and must end with a period.
- Placeholder text is forbidden. Do not use values like:
  - `operation`
  - `input parameter`
  - `result`
  - `performs side effects defined by this operation`
- Do not use old comment style `// Function / // Param / // Return`.

## JavaDoc Consistency (Mandatory)
- Use one style consistently in the repository:
  - Full-sentence style with ending period for description, `@param`, and `@return`.
- Do not mix styles in the same block (for example: description has period but tags do not).
- Constructor JavaDoc must still include `@return` with side-effect summary text (per this repository convention).

## Agent Read Protocol (Mandatory)
- Before making any code change, the agent must:
  1. Read `AGENTS.md` fully.
  2. Output a short "Rule-Check" summary listing rules applied for the current task.
  3. Define pass/fail verification commands before editing.
- Before final answer, the agent must output a short "Compliance Check" stating:
  - Which mandatory rules were checked.
  - Which commands were run.
  - Pass/fail result.

## Naming and API Contract
- Use intention-revealing names; avoid abbreviations except common acronyms.
- Keep method signatures stable unless a bug fix requires change.
- Keep backward compatibility for event payload fields and public APIs.

## Multi-Tenancy and Data Safety
- All repository query methods must be tenant-aware where business data is involved.
- Service layer must validate tenant ownership before mutating state.
- Never read/update cross-tenant data in request flow.

## Concurrency and Idempotency
- Handle optimistic locking conflicts with domain exception mapped to HTTP 409.
- Reserve/confirm/release flows must be idempotent for repeated requests.
- Prefer explicit state checks before mutation.

## Observability
- Include `tenantId`, `orderId`, and `productId` in MDC where available.
- Always clear MDC and thread-local context after request/operation completion.
- Keep logs structured, concise, and correlation-friendly.

## Scheduler and Distributed Lock
- Scheduled jobs that mutate business state must use ShedLock.
- Ensure lock name is stable and unique per job.
- Do not run critical scheduled mutation without lock protection.

## Testing and Verification
- For each meaningful change, run at least module-level compile or tests.
- Keep test profile isolated from external dependencies.
- Add/adjust tests for bug-fix behavior when practical.

## Report Rule (Mandatory)
- After every completed agent execution, write a summary to `REPORT.md` as follows:
  1. Preserve the original header content of the file.
  2. Insert the execution summary immediately below the header, describing clearly the changes made.
  3. At the bottom of the file, append a version note with:
     - Version marker (`v2`, `v3`, ...) in increasing order.
     - Timestamp of the update.
     - Short description of the changes.
- Historical entries and version notes must remain intact for traceability.

## Build & Verification (Mandatory)
- **Tech Stack**: Spring Boot 4.0.2, Java 25, Maven (aggregator `pom.xml` at root).
- **Build Command**: `mvn clean verify` (full stack) or `mvn -q -pl <module-name> -am verify` (single module + dependencies).
- **Module Dependencies**:
  - All services depend on `event-contract` (1.0.0) for shared event envelopes, canonical event payloads, and JSON schemas.
  - Event-producing services (order, payment, inventory, product, notification) depend on `event-infra` (1.0.0) for Kafka outbox, retry, DLQ, tracing, and resilience support.
  - Always declare explicit dependency versions in `pom.xml`.
- **Module Structure**: `src/main/java` packages follow `huynv.<service-name>` (e.g., `huynv.orderservice`, `huynv.paymentservice`); shared modules use `huynv.event`, `huynv.eventinfra`.

## Event-Driven Architecture (Mandatory)
- **Event Contract**: All Kafka events are enveloped as `BaseEvent<T>` defined in `event-contract` where `T` is the business payload (e.g., `PaymentCompletedEvent`).
- **Envelope Structure**: `eventId` (globally unique ULID), `eventType` (canonical topic name), `eventVersion`, `tenantId`, `correlationId`, `causationId`, `timestamp`, `data`.
- **Consumer Pattern**: Consumers are registered via `@ConditionalOnProperty` and `@Component` classes with explicit `objectMapper.readValue()` parsing; NOT using `@KafkaListener` annotations.
- **Consumer Idempotency** (Mandatory):
  - Kafka consumers must check `processed_events(event_id, consumer_service)` before processing.
  - Use `JdbcIdempotencyService` injected from `IdempotencyConfig` to call `alreadyProcessed(eventId)` and `markProcessed(eventId)` within the same transaction as side effects.
  - Record markers in the same database transaction as the business mutation to ensure atomicity.
  - Failure to enforce consumer idempotency will result in duplicate event processing across retries and rebalances.
- **Event Schemas**: JSON schemas are classpath-loaded from `event-contract/src/main/resources/schemas/` and validated during deserialization via `JsonSchemaValidationService`.
- **DLQ & Retry Handling**:
  - Failed Kafka messages are routed to `<topic>-dlq` by the listener container error handler.
  - Services do NOT retry indefinitely; instead, exhausted retries transition messages to DLQ.
  - DLQ replay is opt-in and must be enabled explicitly via configuration and is delegated to `DlqReplayService` in `event-infra`.

## Saga Orchestration & Cross-Service Flows (Mandatory)
- **Pattern**: Order-centric orchestration uses a state machine (`OrderSagaState`: RESERVE_STOCK → CHARGE_PAYMENT → CONFIRM_STOCK → COMPLETED or COMPENSATING).
- **Saga Persistence**: Each saga is persisted in a dedicated `<service>_sagas` table with state, retry count, error message, and resumable step metadata.
- **Step Atomicity**: Each step is guarded by `@Transactional(propagation = Propagation.REQUIRES_NEW)` to ensure crash-safe resume on container restart.
- **Compensating Transactions**: On failure, attempt refund (if paymentId exists) then release reservation. Persistence of `paymentId` before confirm step enables safe recovery.
- **Saga Resume**: Scheduled background task (`SagaResumeService` or equivalent) scans for in-flight sagas and executes next step idempotently.
- **Saga Testing**: Include unit tests that verify saga state transitions, compensation attempts, and crash-safe step resume scenarios.

## Request Idempotency (Mandatory)
- **Scope**: REST command endpoints (create, pay, cancel) must enforce idempotency via `idempotencyKey` header or request field.
- **Implementation**:
  - Check `IdempotencyKey(key, status, response)` table at command start; if exists and COMPLETED, return cached response; if PROCESSING, return deterministic in-flight response.
  - On start: insert row with status PROCESSING.
  - On success: update row to COMPLETED and store serialized response.
  - On failure: update row to FAILED and store error response.
- **Deterministic In-Flight Responses**: For order creation, construct response with orderId from the idempotency key row so repeated calls never generate 409 conflicts.
- **TTL**: Idempotency rows should be purged after ~24 hours via a scheduled job to prevent unbounded table growth.

## Kafka Event Publishing (Mandatory)
- **Transactional Outbox Pattern**: Never publish events directly to Kafka. Instead:
  1. Insert business entity (Order, Payment, etc.) into main table.
  2. Insert corresponding `OutboxEvent` into `<service>_outbox` table with status PENDING, topic, key, and serialized envelope.
  3. Commit transaction atomically.
- **Outbox Publisher Loop**: Scheduled service (`KafkaOutboxPublisher` or equivalent) polls PENDING outbox rows, publishes to Kafka, and transitions to SENT on broker acknowledgment.
- **At-Least-Once Semantics**: SENT rows are retained until explicity purged; if the publisher crashes before updating state, the next poll republishes the same row with the same key, resulting in idempotent replay.
- **Retry Budget**: Outbox messages that fail repeatedly (e.g., poisoned JSON or broker unavailability) are exhausted to DLQ after a configured retry limit.
- **Idempotent Keys**: Outbox rows use (tenantId, entity-id, event-type) as the Kafka message key to guarantee ordering per tenant and entity.

## Multi-Tenancy & Data Safety (Mandatory - Extends Existing)
- **Context Extraction**: Servlet filters (`UserContextFilter`) extract `X-Tenant-Id`, `X-User-Id`, `X-Roles`, `X-Request-Id`, `X-Correlation-Id` headers and populate `UserContext` thread-local and MDC.
- **MDC Cleanup**: After request completion, explicitly clear MDC to prevent cross-request contamination in thread pools.
- **Gateway Trust Boundary**: `TrustedRequestContextFilter` in gateway strips inbound identity headers and reinjected trusted values from validated JWT tokens. Services must NOT directly trust `X-Tenant-Id` headers from external clients; only from gateway.
- **Repository Queries**: All queries must filter by tenantId; use repository methods like `findByTenantIdAndStatus()` rather than `findAll()`.
- **Query Annotations**: For complex queries, annotate with tenant-scoping via JPA named queries or Spring Data query methods explicitly including tenantId condition.

## Observability & Tracing (Mandatory - Extends Existing)
- **OpenTelemetry Integration**: Services auto-configure OTel via Spring Boot starter; spans are created automatically for HTTP, Kafka, and database operations.
- **Span Annotations**: For custom business logic, use `@WithSpan` or programmatic span creation to demarcate critical paths (e.g., saga step execution, payment provider calls).
- **Trace Context Propagation**: MDC and OTel context are automatically propagated across service boundaries via `MdcPropagationExchangeFilter` and Kafka headers.
- **Structured Logging**: Log JSON payloads with tags (tenantId, orderId, productId, eventId, sagaId, paymentId) instead of unstructured strings.
- **Health & Metrics**:
  - `/actuator/health`: Liveness, readiness, and segment probes.
  - `/actuator/prometheus`: Micrometer metrics tagged with `application=<service-name>`, `tenantId`, `operation`, etc.
  - Outbox and DLQ depths must be published as gauges for alerting.

## Concurrency & Locking (Mandatory - Extends Existing)
- **Optimistic Locking**: Use Hibernate `@Version` on aggregate roots (Order, Payment, Product). Update conflicts trigger `OptimisticLockingFailureException` → HTTP 409.
- **Pessimistic Locking**: For critical sagas, use `SELECT ... FOR UPDATE SKIP LOCKED` in custom queries (e.g., `lockReadyBatch()`) to prevent concurrent saga step execution.
- **ShedLock**: Scheduled jobs that mutate business state (saga resume, outbox publisher, DLQ replay) must use ShedLock with stable lock names (e.g., `order-saga-resume`, `outbox-publisher`).
- **Kafka Consumer Groups**: Configure per-service consumer groups (e.g., `payment-service`, `order-view-service`) and ensure concurrency settings are tuned (default `concurrency=3` is typical).

## <Service Name> Overview (Template for Service-Specific Docs)
1. Purpose
   - Describe the goal of the service and the business problem it solves.
2. Key Functions
   - List the main functions (e.g., order processing, payment validation).
3. Event Flows (if applicable)
   - Describe inbound events consumed (e.g., from other services) and outbound events published.
   - Reference saga states or choreography patterns used.
4. Tech Stack
   - Programming languages, frameworks, DB, cache, messaging, specific to this service.
5. APIs / Endpoints
   - List important REST endpoints, gRPC services, internal APIs; basic input/output; authentication requirements.
6. Notes / Best Practices
   - Service-specific multi-tenancy rules, idempotency patterns, logging/observability, concurrency quirks, known limitations.
