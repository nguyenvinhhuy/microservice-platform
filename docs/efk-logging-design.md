# EFK Logging Design

## Purpose

This document defines a Kubernetes-native EFK design for the microservice platform. The design treats logging as cluster infrastructure rather than as a business microservice and keeps `audit-log-service` focused on immutable business audit events.

## Goals

- Centralize operational logs from all platform workloads.
- Preserve Kubernetes metadata such as namespace, pod, container, and node.
- Support correlation searches using `tenantId`, `orderId`, `productId`, `eventId`, `sagaId`, and `correlationId`.
- Keep Elasticsearch for logging isolated from Elasticsearch used by `product-service` search workloads.
- Maintain a production-safe separation between business audit data and runtime logs.

## Non-Goals

- Replacing `audit-log-service`.
- Parsing every business payload into first-class Elasticsearch fields on day one.
- Using Kubernetes host filesystem logs as the primary operator interface.

## Recommended Topology

### Namespaces

- `microservice-platform`: Business services and application workloads.
- `logging`: EFK stack resources.

### Components

1. `Fluentd` as a `DaemonSet`
   - Runs one pod per node.
   - Tails `/var/log/containers/*.log`.
   - Enriches each record with Kubernetes metadata.
   - Sends logs to the logging Elasticsearch cluster.

2. `Elasticsearch` as a `StatefulSet`
   - Dedicated for operational logging.
   - Uses persistent storage.
   - Exposes a cluster-internal service for Fluentd and Kibana.

3. `Kibana` as a `Deployment`
   - Provides log search, saved views, and operational dashboards.

4. Existing application services
   - Continue to write structured logs to `stdout` and `stderr`.
   - Do not write log files inside containers.
   - Do not embed EFK-specific shipping logic in business services.

## Separation of Responsibilities

### `audit-log-service`

- Consumes Kafka business events.
- Persists tenant-aware audit history in PostgreSQL.
- Supports compliance, traceability, and event-history queries.

### EFK

- Collects runtime application logs and infrastructure-facing pod logs.
- Supports incident response, troubleshooting, and operational search.
- Tracks stack traces, retries, timeouts, consumer failures, and pod lifecycle issues.

## Logging Flow

1. A platform service writes JSON logs to `stdout` or `stderr`.
2. Kubernetes writes the container log file on the node.
3. Fluentd tails `/var/log/containers/*.log`.
4. Fluentd enriches the record with Kubernetes metadata and selected labels.
5. Fluentd routes the record into Elasticsearch indices such as `logs-microservice-platform-YYYY.MM.DD`.
6. Kibana queries and visualizes the indexed logs.

## Log Format Standard

All services should produce structured JSON logs. Each record should include the following fields when available:

- `timestamp`
- `level`
- `message`
- `serviceName`
- `tenantId`
- `userId`
- `orderId`
- `productId`
- `paymentId`
- `eventId`
- `sagaId`
- `correlationId`
- `causationId`
- `traceId`
- `spanId`

## Index Strategy

Recommended index groups:

- `logs-microservice-platform-*`
  - General application logs for all business services.
- `logs-microservice-platform-audit-*`
  - Optional runtime logs emitted by `audit-log-service`.
- `logs-infrastructure-*`
  - Optional node, ingress, or platform-component logs if collected later.

Recommended policy:

- Keep application logs for 7 to 14 days in non-production.
- Keep production logs for 14 to 30 days based on storage budget.
- Use rollover and delete lifecycle policies rather than unbounded growth.

## Elasticsearch Isolation

The repository already has `product-service` search concerns. Logging and product search should not share the same Elasticsearch workload without clear isolation. Prefer one of the following:

1. Separate Elasticsearch clusters.
2. Separate node pools within the same Elasticsearch deployment.
3. At minimum, separate index templates, ILM policies, and resource limits.

The first option is preferred for staging and production.

## Kubernetes Resource Layout

Place logging manifests in `k8s/logging/`:

- `00-logging-namespace.yaml`
- `01-elasticsearch-config.yaml`
- `02-elasticsearch.yaml`
- `03-kibana.yaml`
- `04-fluentd-rbac.yaml`
- `05-fluentd-config.yaml`
- `06-fluentd-daemonset.yaml`
- `README.md`

This keeps cluster logging infrastructure separate from the business deployment manifests already stored in `k8s/`.

## Deployment Order

1. Create the `logging` namespace.
2. Deploy Elasticsearch config and storage-backed workload.
3. Deploy Kibana after Elasticsearch is ready.
4. Deploy Fluentd last so it can start shipping logs immediately.

## Operational Queries to Support

Kibana views should support queries such as:

- `kubernetes.namespace_name:"microservice-platform" AND serviceName:"order-service"`
- `tenantId:"1001" AND correlationId:"..."`
- `serviceName:"payment-service" AND level:"ERROR"`
- `serviceName:"inventory-service" AND eventId:"..."`
- `kubernetes.pod_name:"gateway-service-..." AND traceId:"..."`

## Security and Secrets

- Do not store Elasticsearch credentials in source control.
- Use Kubernetes `Secret` objects for Fluentd and Kibana credentials.
- Prefer TLS between Fluentd, Kibana, and Elasticsearch in staging and production.

## Resource Guidance

Suggested starting point for a small non-production cluster:

- Elasticsearch:
  - `requests`: `500m` CPU, `1Gi` memory.
  - `limits`: `1` CPU, `2Gi` memory.
- Kibana:
  - `requests`: `200m` CPU, `512Mi` memory.
  - `limits`: `500m` CPU, `1Gi` memory.
- Fluentd:
  - `requests`: `100m` CPU, `200Mi` memory.
  - `limits`: `300m` CPU, `500Mi` memory.

These values must be tuned using real log volume and retention data.

## Rollout Guidance

### Phase 1

- Deploy EFK only for `microservice-platform` namespace.
- Collect container logs only.
- Keep one shared application index.

### Phase 2

- Standardize JSON logging fields across services.
- Add saved searches and dashboards in Kibana.
- Add retention policies and alerting on Elasticsearch health.

### Phase 3

- Split indices by environment and log class if needed.
- Add infrastructure logs and ingress logs.
- Add alerting for log ingestion failures and Fluentd backpressure.

## Verification

After deployment, confirm:

1. Fluentd pods are running on every node.
2. Elasticsearch health is green or yellow as expected for the environment.
3. Kibana can access the `logs-microservice-platform-*` index pattern.
4. A new log line from `gateway-service` appears in Kibana with Kubernetes metadata.
5. A search by `correlationId` returns related lines from multiple services when present.

## Decision Summary

- Use EFK as cluster infrastructure under `k8s/logging/`.
- Keep `audit-log-service` as business audit storage.
- Keep application logs on `stdout` and `stderr`.
- Keep logging Elasticsearch isolated from product-search Elasticsearch.
