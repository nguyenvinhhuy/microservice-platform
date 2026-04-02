# ledger-schema.md
# Double Entry Ledger Design

## Overview

Ledger ensures financial correctness.

Invariant:

sum(debit) == sum(credit)

---

# Tables

## accounts

account_id
account_type
owner_id
currency
created_at

Account types:

CUSTOMER
MERCHANT
PLATFORM
GATEWAY_CLEARING

---

# transactions

transaction_id
reference_id
type
created_at

Transaction types:

PAYMENT
REFUND
FEE
ADJUSTMENT

---

# ledger_entries

entry_id
transaction_id
account_id
debit
credit
currency
created_at

---

# Example Payment

Customer pays $100
Platform fee = $3
Merchant receives $97

Entries:

Customer account  debit 100
Merchant account  credit 97
Platform account  credit 3

---

# Example Refund

Refund $50

Entries:

Merchant account debit 50
Customer account credit 50

---

# Ledger Rules

1. Every transaction must balance.
2. Ledger entries are immutable.
3. Corrections require compensating transactions.
4. Ledger is source of financial truth.