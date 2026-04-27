# technical.md
# E-Commerce Payment Platform – Full Technical Architecture Specification

---

# 1. Overview

This document defines the architecture and technical requirements for a production-grade **Payment Platform** used in an E-Commerce system.

Goals:

- Prevent double charging
- Guarantee financial correctness
- Ensure high reliability
- Provide full auditability
- Support multiple payment providers
- Handle high traffic scenarios (flash sales)
- Enable safe retries and failure recovery

This specification is designed to be used by both engineers and AI coding agents.

---

# 2. Core Engineering Principles

## Financial Correctness

Money must always be consistent.

Rules:

- Money must never disappear
- Money must never appear incorrectly
- All financial operations must be recorded in a **double-entry ledger**

---

## Idempotency

All write operations must be idempotent.

Applies to:

- Payment creation
- Capture
- Refund
- Webhook processing
- Event processing

Retries must be safe.

---

## Auditability

Every financial action must be traceable.

Audit trail must include:

- user
- order
- payment
- gateway interaction
- ledger movement
- timestamps

---

## Fault Tolerance

System must tolerate:

- network failures
- payment gateway failures
- duplicate requests
- duplicate events
- partial failures
- service restarts

---

# 3. High Level Architecture

Client
↓
API Gateway
↓
Payment API Service
↓
Idempotency Layer
↓
Payment Orchestrator

Supporting services:

- Fraud Detection Service
- Tokenization Service
- Ledger Service
- Refund Service
- Webhook Processor
- Reconciliation Service

Infrastructure:

- Event Bus (Kafka / message queue)
- Cache (Redis)
- Database (PostgreSQL)

External systems:

- Payment gateways
- Banks
- Wallet providers

---

# 4. Core Services

## Payment API Service

Responsibilities:

- API request validation
- authentication / authorization
- idempotency handling
- payment creation
- payment query
- refund requests

Example APIs:

POST /payments
GET /payments/{payment_id}
POST /payments/{payment_id}/capture
POST /payments/{payment_id}/refund
POST /payments/{payment_id}/cancel

---

## Payment Orchestrator

Controls payment lifecycle.

Responsibilities:

- manage payment state machine
- coordinate gateway interactions
- invoke fraud checks
- update ledger
- publish payment events

Payment State Machine:

CREATED
PROCESSING
AUTHORIZED
CAPTURED
FAILED
REFUNDED
CANCELLED

---

## Payment Gateway Adapter

Abstracts different payment providers.

Common Interface:

authorize(payment)
capture(payment)
void(payment)
refund(payment)
verify_webhook(payload)

Adapters may include:

StripeAdapter
PayPalAdapter
AdyenAdapter
LocalBankAdapter

---

# 5. Payment Data Model

## Payment

Fields:

payment_id
order_id
user_id
amount
currency
status
payment_method
idempotency_key
created_at
updated_at

Constraints:

UNIQUE(order_id)
UNIQUE(idempotency_key)

---

## PaymentAttempt

Represents gateway interaction.

Fields:

attempt_id
payment_id
gateway
status
response_code
response_payload
created_at

---

## Refund

Fields:

refund_id
payment_id
amount
reason
status
created_at

---

# 6. Idempotency Design

All write APIs must require a dedicated business idempotency contract.

For the current REST payment command implementation, that contract is the
`Idempotency-Key` header.

Example:

POST /payments
Idempotency-Key: uuid

Tracing headers such as `X-Request-Id` are complementary observability data and
must not replace the business idempotency key.

Storage:

idempotency_key
request_hash
response_payload
status
expires_at

Logic:

1. receive request
2. check key
3. if exists → return stored response
4. else → process request
5. store response

---

# 7. Payment Flow

## Standard Payment Flow

1. user clicks pay
2. API receives payment request
3. idempotency check
4. fraud check
5. create payment record
6. authorize payment via gateway
7. update payment status
8. update ledger
9. publish payment event
10. confirm order

---

## Payment Retry Flow

Possible retry triggers:

- network timeout
- client retry
- gateway retry

System must detect duplicate requests using idempotency.

---

## Payment Failure Flow

Possible failures:

- gateway error
- fraud detection
- insufficient funds
- network timeout

Failure must:

- update payment state
- log reason
- publish failure event

---

# 8. Refund Flow

Refund types:

- full refund
- partial refund
- multiple refunds

Refund steps:

1. validate payment
2. validate refund amount
3. call gateway refund
4. update ledger
5. publish refund event

---

# 9. Webhook Processing

Payment gateways send webhooks.

Examples:

payment_success
payment_failed
refund_processed
chargeback_created

Webhook processing must:

- verify signature
- validate payload
- ensure idempotency
- update payment status
- emit internal events

---

# 10. Event Driven Architecture

All payment lifecycle events must be published.

Events:

payment.created
payment.processing
payment.authorized
payment.captured
payment.failed
payment.refunded
chargeback.created

Consumers:

Order Service
Inventory Service
Notification Service
Analytics Service
Accounting Service

---

# 11. Fraud Detection

Fraud checks may include:

Velocity rules:

- too many payments in short time
- multiple cards per user
- repeated failed attempts

Risk scoring inputs:

IP address
device fingerprint
country mismatch
user history

Possible actions:

block payment
require extra verification

---

# 12. Ledger System (Double Entry Accounting)

Ledger guarantees financial correctness.

Rule:

sum(debit) == sum(credit)

Example Payment:

Customer pays $100
Platform fee = $3

Ledger entries:

Customer Account   debit 100
Merchant Account   credit 97
Platform Account   credit 3

Ledger rules:

- entries immutable
- corrections via compensating transactions

---

# 13. Reconciliation

Daily reconciliation process.

Compare:

internal ledger
vs
gateway settlement report

Steps:

1. download settlement report
2. compare transaction records
3. detect mismatches
4. flag discrepancies

---

# 14. Chargeback Handling

Chargeback flow:

1. gateway sends chargeback webhook
2. mark payment disputed
3. notify merchant
4. collect evidence
5. update ledger if chargeback confirmed

---

# 15. Retry Strategy

Retries must use:

exponential backoff

Retry triggers:

gateway timeout
temporary gateway failure

Max retry attempts must be configured.

---

# 16. Reliability Patterns

Required patterns:

retry strategy
circuit breaker
dead letter queue
timeout handling

---

# 17. Caching Strategy

Use Redis for:

idempotency lookup
payment status cache
fraud rule cache
gateway configuration

Database remains the source of truth.

---

# 18. Observability

System must expose:

Metrics:

payment_success_rate
gateway_latency
refund_rate
fraud_detection_rate

Logging must include:

payment_id
order_id
request_id
user_id

Distributed tracing must track full payment lifecycle.

---

# 19. Security Requirements

Security rules:

PCI DSS compliance
TLS encryption
encryption at rest
tokenization

Never store:

card number
CVV

Sensitive data must never appear in logs.

---

# 20. Deployment Architecture

Deployment requirements:

containerized services
Kubernetes orchestration
horizontal scaling

Production setup:

multi-region deployment
active-active setup
global traffic routing

---

# 21. Multi Gateway Routing

Support multiple payment providers.

Routing strategies:

primary gateway
fallback gateway
smart routing

Example:

Stripe failure → fallback to Adyen.

---

# 22. Checklist for Implementation

Core modules:

Payment API
Payment Orchestrator
Idempotency Layer
Gateway Adapter
Fraud Service
Ledger Service
Refund Service
Webhook Processor
Reconciliation Service

Infrastructure:

PostgreSQL
Redis
Kafka

Observability:

metrics
logging
tracing

Security:

PCI compliance
tokenization
encrypted storage

---

# End of Technical Specification