# Order Saga Contract

## Scope
- Service: `order-service`
- Coordinator: `huynv.orderservice.saga.OrderSagaCoordinator`
- Persistence: table `order_sagas`

## Saga States
- `RESERVE_STOCK`
- `CHARGE_PAYMENT`
- `CONFIRM_STOCK`
- `COMPLETED`
- `COMPENSATING`

## Create Order Saga
1. Persist `Order` with status `CREATED`.
2. Persist `OrderSaga` with state `RESERVE_STOCK`.
3. Call inventory reservation.
4. On success:
   - Transition `Order` to `RESERVED`.
   - Transition `OrderSaga` to `CHARGE_PAYMENT`.
5. On failure:
   - Transition `Order` to `FAILED`.
   - Attempt reservation release.
   - Transition `OrderSaga` to `COMPENSATING`.

## Pay Order Saga
1. Lock and transition `Order` to `PAYMENT_IN_PROGRESS`.
2. Transition `OrderSaga` to `CHARGE_PAYMENT`.
3. Call payment charge.
4. Persist `paymentId`, transition `OrderSaga` to `CONFIRM_STOCK`.
5. Call inventory confirm.
6. On success:
   - Transition `Order` to `CONFIRMED`.
   - Transition `OrderSaga` to `COMPLETED`.
7. On failure:
   - Execute compensation (refund/release).
   - Transition `Order` to `FAILED` or `COMPENSATING`.

## Cancel Order Saga
1. Release inventory reservation when order is reserved/in-progress.
2. Transition `Order` to `CANCELLED`.
3. Transition `OrderSaga` to `COMPLETED` when saga row exists.

## Recovery Contract
- Resume worker scans non-terminal saga rows.
- Step replay is idempotent by state + persisted references.
- Retry count and last error are persisted on saga row.
