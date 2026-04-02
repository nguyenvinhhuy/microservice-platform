# Event Schema Evolution (Platform Standard)

This platform uses a JSON event envelope with a versioned `dataSchema` field:

```json
{
  "eventId": "...",
  "eventType": "product.price.updated",
  "source": "product-service",
  "eventTime": "...",
  "aggregateId": "...",
  "aggregateVersion": 3,
  "dataSchema": "product.price.updated.v1",
  "traceId": "...",
  "correlationId": "...",
  "causationId": "...",
  "data": {}
}
```

## Rules

1. Events must be backward compatible.
2. Existing fields MUST NEVER be removed or renamed.
3. New fields MUST be optional (nullable) and MUST have safe defaults.
4. Consumers MUST ignore unknown fields during deserialization.
5. Schema version increments when the `data` payload changes:
   - `product.price.updated.v1`
   - `product.price.updated.v2`

## Producer Requirements

- Producers MUST set `dataSchema` to a versioned schema identifier.
- Producers SHOULD only make additive payload changes.
- Producers SHOULD keep old versions available for a deprecation window when introducing a new version.

## Consumer Requirements

- Consumers MUST support multiple `dataSchema` versions for the same `eventType` concurrently.
- Consumers MUST treat unknown `dataSchema` versions as forward-compatible when possible:
  - Prefer parsing known fields and ignoring unknown ones.
  - Route truly incompatible versions to DLQ (poison message isolation).

## Example: Additive Change

If `product.price.updated.v1` contains:

```json
{ "productId": "p1", "price": 10.0, "currency": "USD" }
```

Then `product.price.updated.v2` may add an optional field:

```json
{ "productId": "p1", "price": 10.0, "currency": "USD", "priceListId": "default" }
```

Consumers that ignore unknown fields continue to process `v2` without changes, and consumers that require the new field can explicitly switch on `dataSchema`.

