# Kibana Query Cheatsheet

## Purpose

This document provides practical Kibana queries for the microservice platform after the MDC and JSON logging standardization. The examples assume logs are collected into the `logs-microservice-platform-*` index pattern.

## Recommended Kibana Setup

- Create an index pattern such as `logs-microservice-platform-*`.
- Set `timestamp` as the time field.
- Add the following fields to the default table view when useful:
  - `timestamp`
  - `serviceName`
  - `level`
  - `message`
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
  - `kubernetes.pod_name`

## Field Guide

- `serviceName`: Spring service name emitted by `logback-spring.xml`.
- `tenantId`: Tenant boundary identifier.
- `userId`: User identifier when present in request or event context.
- `orderId`: Order identifier for order, payment, and inventory flows.
- `productId`: Product identifier for product and inventory flows.
- `paymentId`: Payment identifier for payment-related flows.
- `eventId`: Kafka or domain event identifier.
- `sagaId`: Saga identifier when business flow uses a persisted saga.
- `correlationId`: Cross-service request and event correlation identifier.
- `traceId`: OpenTelemetry trace identifier.
- `topic`, `partition`, `offset`: Kafka processing metadata on consumer-side logs.

## Basic Queries

All logs from the application namespace:

```text
kubernetes.namespace_name:"microservice-platform"
```

All error logs:

```text
level:"ERROR"
```

All logs for one service:

```text
serviceName:"order-service"
```

Gateway traffic only:

```text
serviceName:"gateway-service" AND logger:"huynv.gatewayservice.filters.StructuredGatewayLoggingFilter"
```

## Request Tracing Queries

One request by correlation id:

```text
correlationId:"<correlation-id>"
```

One request by trace id:

```text
traceId:"<trace-id>"
```

One request by request id:

```text
requestId:"<request-id>"
```

Gateway request plus downstream services for the same business flow:

```text
correlationId:"<correlation-id>" AND serviceName:("gateway-service" OR "order-service" OR "payment-service" OR "inventory-service" OR "notification-service")
```

## Tenant and User Queries

All logs for one tenant:

```text
tenantId:"1001"
```

All logs for one tenant and one user:

```text
tenantId:"1001" AND userId:"2002"
```

Errors affecting one tenant:

```text
tenantId:"1001" AND level:"ERROR"
```

## Order Flow Queries

Everything related to one order:

```text
orderId:"<order-id>"
```

Order flow across core services:

```text
orderId:"<order-id>" AND serviceName:("order-service" OR "payment-service" OR "inventory-service" OR "order-view-service" OR "notification-service")
```

Order flow errors only:

```text
orderId:"<order-id>" AND level:"ERROR"
```

Order saga correlation view:

```text
orderId:"<order-id>" AND (sagaId:* OR correlationId:*)
```

## Product Flow Queries

Everything related to one product:

```text
productId:"<product-id>"
```

Product write flow and projection:

```text
productId:"<product-id>" AND serviceName:("product-service" OR "inventory-service" OR "product-view-service")
```

Product event troubleshooting:

```text
productId:"<product-id>" AND eventId:"<event-id>"
```

## Payment Flow Queries

Everything related to one payment:

```text
paymentId:"<payment-id>"
```

Payment failure investigation:

```text
serviceName:"payment-service" AND paymentId:"<payment-id>" AND level:"ERROR"
```

Payment flow for one order:

```text
serviceName:"payment-service" AND orderId:"<order-id>"
```

## Kafka and Event Queries

One event across all consumers and publishers:

```text
eventId:"<event-id>"
```

All logs for one Kafka topic:

```text
topic:"order.events"
```

One Kafka record by topic, partition, and offset:

```text
topic:"order.events" AND partition:"2" AND offset:"19483"
```

DLQ troubleshooting:

```text
serviceName:"dlq-replayer-service" AND (eventId:"<event-id>" OR originalTopic:"<topic-name>")
```

Audit trail support logs:

```text
serviceName:"audit-log-service" AND eventId:"<event-id>"
```

Projection consumer investigation:

```text
serviceName:("order-view-service" OR "product-view-service") AND eventId:"<event-id>"
```

## Notification Queries

Notification flow for one event:

```text
serviceName:"notification-service" AND eventId:"<event-id>"
```

Notification delivery by tenant and provider:

```text
serviceName:"notification-service" AND tenantId:"1001" AND provider:"<provider-name>"
```

Failed notifications:

```text
serviceName:"notification-service" AND level:"ERROR"
```

## Operational Queries

Errors for one pod:

```text
kubernetes.pod_name:"<pod-name>" AND level:"ERROR"
```

One service on one pod:

```text
serviceName:"gateway-service" AND kubernetes.pod_name:"<pod-name>"
```

Restart or rollout observation for one deployment:

```text
serviceName:"order-service" AND kubernetes.pod_name:"order-service-*"
```

High-noise warning review:

```text
level:"WARN" AND serviceName:"inventory-service"
```

## Suggested Saved Searches

Create saved searches for:

1. `Gateway Requests`
   - Query:
   - `serviceName:"gateway-service" AND logger:"huynv.gatewayservice.filters.StructuredGatewayLoggingFilter"`

2. `Order Flow Errors`
   - Query:
   - `serviceName:("order-service" OR "payment-service" OR "inventory-service") AND level:"ERROR"`

3. `Kafka Consumer Failures`
   - Query:
   - `level:"ERROR" AND topic:*`

4. `Tenant Incident View`
   - Query:
   - `tenantId:"<tenant-id>"`

5. `DLQ Activity`
   - Query:
   - `serviceName:"dlq-replayer-service"`

## Investigation Playbooks

### Request Failed from UI

1. Search by `correlationId`.
2. Filter down to `gateway-service`.
3. Expand to `order-service`, `payment-service`, and `inventory-service`.
4. Check `level:"ERROR"` or mismatched status transitions.

### One Event Did Not Reach Projection

1. Search by `eventId`.
2. Confirm publisher-side logs exist.
3. Check `order-view-service` or `product-view-service`.
4. If missing, check `dlq-replayer-service` and topic-level logs.

### Tenant-Specific Incident

1. Search by `tenantId`.
2. Narrow by time range.
3. Add `level:"ERROR"` if the result set is too large.
4. Expand into `correlationId` or `orderId` once one suspicious flow is found.

## Notes

- Some legacy logs may still emit `service` instead of `serviceName`. Prefer `serviceName` for new searches.
- Kafka metadata fields appear mainly on consumer-side logs where MDC enrichment is applied.
- If a field is missing, confirm the relevant service actually set the value in request or event context before assuming an ingestion problem.
