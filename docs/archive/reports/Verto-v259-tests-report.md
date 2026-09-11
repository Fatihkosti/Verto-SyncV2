# Verto v259 — Tests Report

| Gate | Result |
|---|---|
| `tools/verify_v259_inventory_writer.py` | **PASS — 39/39** |
| SQLite rollback after Movement | **PASS** |
| SQLite rollback on Outbox failure | **PASS** |
| SQLite 20 concurrent writes | **PASS — no lost update** |
| SQLite duplicate Retry | **PASS — no second movement/outbox/delta** |
| Static direct stock-mutator scan | **PASS** |
| `verify_v242_logistics_quality.py` | **PASS** |
| `verify_v245_atomic_invariants.py` | **PASS** |
| `verify_v247_inventory_costing.py` | **PASS** |
| `verify_v248_invoice_lifecycle.py` | **PASS** |
| `verify_v249_financial_sync.py` | **PASS** |
| `verify_v252_invoice_returns.py` | **PASS** |
| `verify_v253_purchase_cycle.py` | **PASS** |
| Kotlin quality debt delta vs v258 | **PASS — no measured increase** |
| Overall historical Kotlin quality gate | **FAIL — pre-existing baseline debt** |
| `InventoryStockWriterV259Test` | **NOT RUN — Gradle/emulator unavailable** |
| `InventoryStockWriterMigration259Test` | **NOT RUN — Gradle/emulator unavailable** |
| Kotlin/Android compilation | **NOT REACHED — Gradle 8.9 download blocked** |
| Room KSP schema export 75 | **NOT GENERATED — Gradle unavailable** |

## Instrumentation coverage added

- 20 concurrent issues on one item.
- Retry same command idempotency.
- forced Movement insert failure rolls back snapshot + command guard.
- forced Outbox conflict rolls back snapshot + movement + command guard.
- manual adjustment requires permission and nonblank reason.

No build or device test is claimed as executed.
