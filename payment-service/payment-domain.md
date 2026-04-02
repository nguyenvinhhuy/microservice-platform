# payment-domain.md
# Payment Domain Model

## Entities

Payment
PaymentAttempt
Refund
PaymentMethod

---

# Payment

Represents a payment for an order.

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

# Payment Status

CREATED
PROCESSING
AUTHORIZED
CAPTURED
FAILED
REFUNDED
CANCELLED

---

# PaymentAttempt

Represents interaction with a payment gateway.

Fields:

attempt_id
payment_id
gateway
status
response_code
response_payload
created_at

---

# Refund

Fields:

refund_id
payment_id
amount
reason
status
created_at

Statuses:

REQUESTED
PROCESSING
SUCCESS
FAILED

---

# PaymentMethod

Types:

CARD
BANK_TRANSFER
EWALLET
COD

Fields:

payment_method_id
user_id
type
token
provider
created_at

Sensitive data must be tokenized.

---

# Domain Rules

1. One order can have only one successful payment.
2. Payment state transitions must be valid.
3. Refund cannot exceed captured amount.
4. All financial movements must be recorded in ledger.