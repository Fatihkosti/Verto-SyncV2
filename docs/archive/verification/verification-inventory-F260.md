# Inventory F260 Verification

## Closure checklist

- [x] Invoice lifecycle includes `DRAFT`, `POSTED`, `VOID`.
- [x] Purchase-cycle lifecycle includes `PARTIALLY_RECEIVED`.
- [x] Editor draft creates no accounting/inventory movement.
- [x] Draft → post does not reverse nonexistent draft stock.
- [x] Each invoice line has stable `sourceLineId`.
- [x] Each invoice line has a distinct deterministic `writeId`.
- [x] All lines share a stable invoice `postingGroupId`.
- [x] Invoice persistence + line stock posting are under one Room transaction owner.
- [x] Local purchase posts accepted stock.
- [x] International purchase remains outside stock until warehouse receipt.
- [x] Local GRN uses fixed Receipt Line ID and line-specific idempotency.
- [x] Partial receipt cannot exceed remaining quantity.
- [x] Accepted quantity only enters stock.
- [x] Shipment receipt movement stores Receipt Line ID + receiving batch group.
- [x] Logistics payment does not mutate inventory quantity.
- [x] Posted financial edits cannot rewrite old movements in place.
- [x] v260 verifier: **33/33 PASS**.
- [x] v259 writer regression: **39/39 PASS**.
- [x] Measured Kotlin quality debt unchanged from v259.
- [ ] Gradle compilation: unavailable because Gradle 8.9 distribution cannot be downloaded.
- [ ] Android/Room instrumentation: not run in this environment.

## Verdict

**F260 implementation gate PASS with external Gradle build/tests pending.**
