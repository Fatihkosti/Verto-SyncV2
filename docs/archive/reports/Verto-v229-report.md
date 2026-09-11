# Verto v229 — Execution Report

Baseline: `Verto-v228.zip`  
Contract: `VERTO_LOGISTICS_REBUILD_MASTER_PLAN_v229-v233_REVISED.md` — Session v229 only.

## Implemented

- Room schema `55 → 56` without destructive fallback.
- New shipment state foundation: `DRAFT → READY → WAITING_DEPARTURE → IN_TRANSIT → AT_STATION → CUSTOMS → RECEIVING → CLOSED`, plus `CANCELLED`.
- `Start Journey` is separated from `Start Movement`; journey start no longer activates a leg.
- Atomic organization-scoped shipment display-number allocator using a dedicated Room transaction/sequence table; display number remains non-PK.
- Explicit purchase truth `LOCAL / INTERNATIONAL` across domain, Room, presentation bridges and sync contract.
- Local purchases post inventory; international purchases skip inventory and are eligible as shipment sources.
- New independent `logistics_payments` persistence/domain foundation linked to `cost_id`; legacy cost payment columns are retained only for v228/Legacy compatibility until v233.
- ISO 4217 currency validation, fixed FX direction `1 foreign unit = X SDG`, and exchange-rate date.
- `occurredAt / recordedAt` event foundation.
- Route-template schema and stop schema.
- Shipping-partner representative name/phone foundation.
- Open/damaged package-count foundation.
- Optional customs milestone/calendar/timezone foundation.
- Friday-off calendar policy abstraction.
- Remote DTO/SQL/RLS contract updated while Logistics V2 remote runtime remains OFF.
- Legacy retained; no Legacy removal was performed.
- Active v229-v233 execution contract copied into project docs and `docs/INDEX.md` updated.

## Added tests

- `MigrationCatalogV229Test`: connected migration catalog from every catalog version `1..55 → 56`.
- `LogisticsV229MigrationTest`: Room migration validation for exported schemas `39..55 → 56`.
- `LogisticsShipmentNumberConcurrencyTest`: concurrent organization-scoped numbering and independent org sequences.
- `InvoicePurchaseScopeInventoryTest`: local purchase posts stock; international purchase does not.
- `LogisticsV229FoundationTest`: state/currency/calendar foundation.

## Verification

| Gate | Result |
|---|---|
| Architecture Guard vs v228 baseline | **PASS** |
| Design System scanner | **PASS — 0 new violations** |
| SQLite smoke for actual exported schema 55 → migration 56 | **PASS** |
| SQLite foreign-key check after 55→56 | **PASS** |
| Direct Kotlin compile/run of v229 state/currency/calendar foundation | **PASS** |
| Gradle unit/instrumentation tests | **BLOCKED BY ENVIRONMENT** |
| `assembleDebug` | **BLOCKED BY ENVIRONMENT** |

### Gradle blocker

The project wrapper requires Gradle `8.9`. This execution environment has no complete cached Gradle 8.9 distribution and network access is disabled, so the wrapper fails while trying to reach `services.gradle.org`. The source was not modified to bypass this gate and no test/scanner was disabled.

Therefore the implementation is packaged, but the final Gradle Build/Test gate is **not claimed as PASS** in this environment.

## Migration coverage note

The migration catalog remains connected from schema 1 through 56. Room schema snapshots present in the repository start at version 39, so full `MigrationTestHelper` structural validation is authored for 39→56 through 55→56; versions 1→38 are covered by catalog-continuity tests but do not have historical exported Room snapshots in v228.

## Remote deployment note

`docs/sql/logistics_v2/010_v229_foundation.sql` contains the additive remote schema/RLS changes, including `purchase_scope`. It was packaged but **not applied to a live server** here. Logistics V2 remote activation remains OFF as required by the contract.
