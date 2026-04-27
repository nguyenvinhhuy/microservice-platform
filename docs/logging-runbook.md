# Logging Runbook

## Purpose

This runbook explains where operators should look first when investigating incidents in the microservice platform. It distinguishes between business audit history, centralized runtime logs, and node-level Kubernetes logs so teams can choose the right source quickly.

## Three Log Sources

### 1. Kibana / EFK

Use Kibana first for:

- Request failures across multiple services.
- Error logs, stack traces, retries, and timeouts.
- Kafka consumer processing issues.
- Order, payment, inventory, product, and notification flow tracing.
- Pod-level troubleshooting when the application is still producing logs.

Primary fields:

- `serviceName`
- `level`
- `tenantId`
- `userId`
- `orderId`
- `productId`
- `paymentId`
- `eventId`
- `sagaId`
- `correlationId`
- `traceId`
- `topic`
- `partition`
- `offset`

### 2. `audit-log-service`

Use `audit-log-service` when you need:

- Immutable business event history.
- Compliance-oriented audit review.
- Tenant-scoped activity history for domain changes.
- Confirmation that a business event was consumed and stored as an audit record.

Do not use `audit-log-service` as the primary source for:

- Stack traces.
- Runtime exceptions.
- Pod crashes.
- Container restarts.
- JVM, database, or network troubleshooting.

### 3. Kubernetes Node or Container Logs

Use node or container logs when:

- The pod never started correctly.
- The app does not emit logs into Kibana.
- There are image pull failures, OOMKills, restart loops, or CNI issues.
- Fluentd is unhealthy or EFK ingestion is delayed.

Typical sources:

- `kubectl logs`
- `kubectl describe pod`
- `/var/log/containers`
- `/var/log/pods`
- kubelet, container runtime, and network logs on the node

## Decision Tree

### The UI request failed

1. Start with Kibana.
2. Search by `correlationId`, `requestId`, or `traceId`.
3. Check `gateway-service` first.
4. Expand into downstream services such as `order-service`, `payment-service`, and `inventory-service`.
5. If the incident is actually about a business event history rather than runtime failure, switch to `audit-log-service`.

### One order or payment behaved incorrectly

1. Start with Kibana.
2. Search by `orderId` or `paymentId`.
3. Check for `level:"ERROR"` and service transitions.
4. Search by `eventId` if Kafka processing is involved.
5. Use `audit-log-service` if you need the immutable sequence of domain events for compliance or post-incident review.

### One product update did not appear in the read model

1. Start with Kibana.
2. Search by `productId`.
3. Check `product-service` for publishing logs.
4. Check `product-view-service` for consumer and projection logs.
5. If there is no consumer-side activity, check DLQ logs and topic metadata.
6. Use node logs only if the pod or container appears unhealthy.

### A Kafka event seems lost

1. Start with Kibana.
2. Search by `eventId`.
3. Check publisher logs.
4. Check consumer logs using `topic`, `partition`, and `offset` if known.
5. Check `dlq-replayer-service` if the event may have gone to DLQ.
6. Check `audit-log-service` only if the event is one of the audited business events and you need to know whether it became part of the audit trail.

### The pod is crash looping or missing from Kibana

1. Skip directly to Kubernetes and node-level logs.
2. Run `kubectl describe pod`.
3. Run `kubectl logs` for the affected pod and container.
4. If needed, inspect `/var/log/containers` or node runtime logs.
5. Only return to Kibana once the pod is actually producing logs again.

## Standard Investigation Workflow

### Application Incident

1. Search Kibana using `correlationId`, `eventId`, or the business identifier.
2. Narrow to the affected service.
3. Expand across related services in the business flow.
4. Filter to `level:"ERROR"` or `level:"WARN"` if the log volume is large.
5. Use `audit-log-service` if you need business-event confirmation or immutable history.

### Infrastructure Incident

1. Check `kubectl get pods`.
2. Check `kubectl describe pod`.
3. Check `kubectl logs`.
4. Check Fluentd, Elasticsearch, and Kibana health in the `logging` namespace.
5. Check node logs if container-level output is missing or incomplete.

## Fast Triage Mapping

### Use Kibana when the symptom is:

- 4xx or 5xx responses.
- Timeout between services.
- Retries or circuit breaker behavior.
- Kafka consumer failure.
- Saga step failure.
- Business flow correlation across multiple services.

### Use `audit-log-service` when the symptom is:

- A user asks what business events happened.
- You need an immutable audit trail for one tenant or one user.
- You need evidence that one domain event was stored for audit review.

### Use node or container logs when the symptom is:

- Pod did not start.
- Pod restarted repeatedly.
- OOMKilled or container runtime issue.
- No application log reached Kibana.
- Fluentd or EFK ingestion appears broken.

## EFK Health Checks

Check these when Kibana seems incomplete:

1. Fluentd pods exist on every node.
2. Elasticsearch pods are healthy.
3. Kibana is reachable.
4. New application logs still arrive in `logs-microservice-platform-*`.
5. Fluentd is not stuck on one node with backlog or parsing errors.

## Common Mistakes

- Searching `audit-log-service` for stack traces or runtime exceptions.
- Jumping to node logs before checking Kibana for normal application failures.
- Assuming an event was never published when the real issue was projection or consumer failure.
- Assuming Kibana ingestion is broken when the pod never emitted logs because it failed before startup.

## Escalation Guidance

Escalate from Kibana to deeper sources in this order:

1. Kibana query by `correlationId`, `eventId`, `orderId`, or `tenantId`.
2. Related service logs in Kibana.
3. `audit-log-service` for business audit confirmation.
4. `dlq-replayer-service` for dead-letter confirmation.
5. `kubectl describe pod` and `kubectl logs`.
6. Node runtime, kubelet, or network logs.

## Related Documents

- [`docs/efk-logging-design.md`](D:/IntelliJProjects/microservice-platform/docs/efk-logging-design.md:1)
- [`docs/kibana-query-cheatsheet.md`](D:/IntelliJProjects/microservice-platform/docs/kibana-query-cheatsheet.md:1)
