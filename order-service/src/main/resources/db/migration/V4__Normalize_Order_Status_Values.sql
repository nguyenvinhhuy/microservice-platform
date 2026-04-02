UPDATE orders
SET status = 'CREATED'
WHERE status = 'PENDING';

UPDATE orders
SET status = 'CONFIRMED'
WHERE status = 'PAID';
