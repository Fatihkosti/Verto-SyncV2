# Verification — Inventory F256

## Scope
Baseline only. No production Kotlin/SQL was intentionally changed.

## Evidence
- Room source of truth: `MigrationCatalog.kt` → schema 72.
- Export: `app/schemas/com.verto.app.data.local.AppDatabase/72.json`.
- Write-path map: `docs/inventory/v256-write-path-map.md`.
- Contract report: `docs/inventory/v256-current-schema-contract.md`.

## Automated static gate
Command: `python3 tools/verify_v256_inventory_baseline.py`
Result: **PASS — 37/37**.

The gate asserts the current v255 baseline, including the facts that must later be removed: mutable quantity snapshot sync, exposed low-level quantity/movement APIs, invoice-edit movement deletion, legacy unsigned movements, and the current shipment/cost paths.

## Gradle gate
Attempted relevant JVM tests. Gradle wrapper tried to download Gradle 8.9; network is disabled and no cached distribution exists. Result: **NOT RUN**, not PASS.

## Instrumentation gate
`InventoryBaselineF256Test` was added but requires Android instrumentation. Result: **NOT RUN** in this environment.

## Change isolation
Production sources: **0 intentional changes**.
Added only tests, verifier, plan copy, and documentation/report artifacts.
