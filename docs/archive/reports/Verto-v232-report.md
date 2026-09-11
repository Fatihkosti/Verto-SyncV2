# Verto v232 — Finalization Session Report

## Scope implemented

- Final receiving is now a two-choice flow: **received completely? Yes / No**.
- The partial path records **missing quantity only**; received quantity is derived as `remaining - missing`.
- v232 receiving posts only accepted/received stock; damaged/rejected/quarantined are forced to zero in this final-receiving flow.
- Shortages support append-only post-close outcomes: **compensated** and **final loss**, while the existing physical recovery path remains intact.
- Late costs can be appended to a **CLOSED** shipment without reopening it.
- Late costs remain **UNPAID** until the existing payment flow is used; cost recognition and cash payment are separate facts.
- Late-cost allocation uses received purchase value as the basis and the Largest Remainder allocator so allocation equals the cost exactly.
- Inventory receipt unit cost is reapplied after late-cost adjustment through the Inventory-owned cost port.
- Permanent deletion now has two paths:
  - DRAFT: direct deletion.
  - Executed shipment: exceptional deletion requiring a reason plus exact shipment-number confirmation.
- Executed permanent deletion reverses paid cash movements and Logistics V2 receipt stock inside the Room transaction before deleting the shipment graph.
- Invoice source links and Logistics-owned relational records are removed by the existing cascade graph; purchase invoices themselves remain immutable.
- Existing correction workflows from v231 were preserved rather than rewritten.

## Persistence

- Room schema version: **59**.
- Added migration: **58 → 59**.
- Added tables:
  - `logistics_shortage_settlements`
  - `logistics_late_cost_adjustments`
  - `logistics_late_cost_allocations`
- Late allocation rows have a direct cascading shipment FK in addition to their line/adjustment relationships, preventing hard-delete orphaning.
- Added SQL mirror/verification files under `docs/sql/logistics_v2/`; remote deletion remains disabled.

## Verification performed

- Design System scanner: **PASS**.
  - Approved legacy exception rules reduced from **395 → 391**.
  - No new raw Material-component debt was admitted.
- Kotlin static debt compared with v231: **no growth**.
  - architecture violations: `18 → 18`
  - dependency cycles: `0 → 0`
  - excessive parameter lists: `457 → 457`
  - large files >500 lines: `10 → 10`
  - long functions: `340 → 340`
  - broad catches: `20 → 20`
  - manual coroutine scopes: `1 → 1`
  - not-null assertions: `0 → 0`
- Pure Kotlin compilation of the v232 domain/application slice: **PASS**.
- Domain behavior smoke test for exact 101-SDG allocation (`25 + 76`) and final-loss settlement: **PASS**.
- SQLite 58→59 migration smoke test: **PASS**; all 3 tables created and shipment cascade deletion succeeds with FK enforcement enabled.
- Logistics session verifier:
  - deleted files: `0`
  - outside allowlist: `0`
  - modules: `31`
  - Room version: `59`
  - migration chain: consecutive
  - only failing check: missing generated `app/schemas/com.verto.app.data.local.AppDatabase/59.json`.

## Environment blocker

A full Gradle/KSP/Room build could not run in this environment because the Gradle 8.9 distribution is not cached and network access to `services.gradle.org` is unavailable (`UnknownHostException`). Therefore the Room 59 schema JSON could not be generated legitimately here. It was intentionally **not fabricated**.

The source implementation is complete for v232, but a full Android build must be run once in an environment with Gradle 8.9 available so Room exports schema 59 and the final build gate can be closed.
