# Ops Kill-Switch

## Purpose
Operational controls to stop non-critical background workers during incidents or maintenance windows.

## Recommended Environment Flags
- `OUTBOX_PUBLISHER_DELAY_MS`
  - Large value disables practical throughput without code change.
  - Example pause: `OUTBOX_PUBLISHER_DELAY_MS=600000`
- `SAGA_RESUME_DELAY_MS`
  - Large value disables practical saga resume loop without code change.
  - Example pause: `SAGA_RESUME_DELAY_MS=600000`

## Runtime Visibility
- Check `outbox_events.status` distribution:
  - `PENDING`, `FAILED`, `SENT`
- Check `order_sagas.state` distribution:
  - non-terminal rows imply paused/retrying orchestration.

## Operational Procedure
1. Increase delay env vars via deployment patch.
2. Verify scheduler activity drops in logs.
3. Perform incident operation.
4. Restore normal delay values.
