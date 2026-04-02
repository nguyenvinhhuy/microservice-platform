# Payment Reconciliation

## Purpose
Reconciliation detects and fixes mismatches between internal payment state and payment provider state.

Typical mismatch:
- internal state = `PROCESSING`
- provider state = `SUCCEEDED`

## Job
`PaymentReconciliationJob` runs periodically and reconciles stale `PROCESSING` payments older than `payment.reconciliation.processing-cutoff-minutes`.

Behavior:
- If provider reports `SUCCEEDED`, payment-service transitions the payment to `SUCCEEDED` and emits `PaymentSucceededEvent`.
- If provider reports `FAILED`, payment-service transitions the payment to `FAILED` and emits `PaymentFailedEvent` with reason `RECONCILE_FAILED`.

## Operational Notes
- Reconciliation runs under ShedLock to avoid duplicate work in Kubernetes.
- Reconciliation must be safe under retries and duplicates and must not double-charge.

