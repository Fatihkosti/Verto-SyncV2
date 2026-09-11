# Verto v239 Implementation Contract

## Goal
Turn an approved logistics plan into controlled execution without conflating preparation, movement, cost, or payment.

## Execution rules
- READY exposes movement preparation first.
- Preparation records actual carrier/contact/cartons/weight while preserving planned snapshots.
- Preparation never sets `startedAt`, `actualDepartureAt`, or IN_TRANSIT.
- First preparation transitions READY -> WAITING_DEPARTURE only.
- Physical movement starts only through `StartLogisticsMovementUseCase` after custody is fully transferred to the actual carrier.
- Movement start records `occurredAt` separately from `recordedAt` and derives ETA from the canonical minute duration.
- Station-to-station preparation is permitted only after the current station is UNLOADED.
- Actual movement cost is separate from payment; optional cash confirmation and proof use independent idempotent request IDs.
- Planned carrier/cargo/cost snapshots are never overwritten by execution facts.
- Duplicate preparation/movement/cost/payment requests must not create duplicate operational or financial facts.

## UI contract
- Show one primary execution step for the current state.
- READY / completed station: `تجهيز الحركة`.
- WAITING_DEPARTURE after custody transfer: `بدأت الحركة`.
- IN_TRANSIT: `تسجيل الوصول`.
- Saved shipping contacts remain selectable and can autofill carrier representative details.
- Planned values appear only as editable defaults for actual execution.

## Invariants
- Room schema stays 61.
- Module count stays 31.
- No migration, dependency, or build-logic changes.
