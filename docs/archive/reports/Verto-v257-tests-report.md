# Verto v257 — Tests Report

| Gate | Result |
|---|---|
| `verify_v257_inventory_ledger_contract.py` | **PASS — 42/42** |
| SQLite duplicate idempotency constraint mirror | **PASS** |
| `verify_v242_logistics_quality.py` | **PASS** |
| `verify_v245_atomic_invariants.py` | **PASS** |
| `verify_v247_inventory_costing.py` | **PASS** |
| `verify_v248_invoice_lifecycle.py` | **PASS** |
| `verify_v249_financial_sync.py` | **PASS** |
| `verify_v252_invoice_returns.py` | **PASS** |
| `verify_v253_purchase_cycle.py` | **PASS** |
| `InventoryLedgerContractV257Test` JVM | **NOT RUN — Gradle 8.9 unavailable; network blocked** |
| `InventoryLedgerMigration257Test` Android/Room | **NOT RUN — requires Gradle + Android device/emulator** |
| Room KSP schema export 73 | **NOT GENERATED in this environment** |
| Postgres/Supabase execution | **NOT RUN — no connected database service** |

## Historical characterization gates

- `verify_v255_analytics_alerts.py` now reports one expected historical assertion failure because it hardcodes `ROOM_SCHEMA_VERSION == 72`.
- `verify_v256_inventory_baseline.py` now reports 35/37 because it intentionally asserts Schema 72 and that the Movement entity is still legacy-only. Both differences are the intended v257 changes, not regressions.

No Build/Test result is reported as PASS unless it actually executed.
