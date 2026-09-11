# Verification — Invoice F247

## Result
PASS for source/static/migration/fixed-point verification. Full Gradle compilation could not start because this environment has no cached Gradle 8.9 distribution and network access is unavailable.

## Acceptance evidence
- Latest-purchase-price policy: 10 @ 100 + 10 @ 200 => 20 @ 200; no weighted average.
- Existing-balance revaluation: 10 × (200 - 100) = 1,000.
- Sale lines persist immutable unit sell price, cost-at-sale, revenue, cost and gross-profit snapshots.
- Later inventory repricing does not rewrite stored sale snapshots.
- International purchase creation remains excluded from inventory posting; accepted receiving remains the stock gate.
- Existing Logistics V2 landed-cost calculation uses accepted quantities only and exact largest-remainder allocation.
- Receiving updates latest buy price; later landed-cost changes reprice current remaining balance and append adjustment/revaluation events.
- Existing-item purchase does not change sell price.
- Invoice-line sale snapshots sync through Supabase DTO/push/pull contract.
- Legacy rows keep historical cost unknown rather than inventing profit/cost.

## Verification commands/results
- `python3 tools/verify_v244_migration_sql.py` => `V244_MIGRATION_SQL_PASS`
- `python3 tools/verify_v245_migration_sql.py` => `V245_MIGRATION_SQL_PASS`
- `python3 tools/verify_v246_currency_truth.py` => `V246_CURRENCY_TRUTH_PASS`
- `python3 tools/verify_v247_inventory_costing.py` => `V247_INVENTORY_COSTING_PASS`
- Kotlin fixed-point harness => `V247_SALE_SNAPSHOT_HARNESS_PASS`
- Conflict-marker scan => clean.

## Build limitation
Attempted:
`./gradlew :feature:invoice:compileDebugKotlin :feature:inventory:compileDebugKotlin :data:database:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon`

The Gradle wrapper attempted to fetch `gradle-8.9-bin.zip` and failed with `UnknownHostException: services.gradle.org`. This is an environment/tooling limitation, not a reported source compile pass.

## Room
- Schema version: 64 -> 65.
- Migration: `MIGRATION_64_65`.
- New tables: `inventory_cost_revaluation_events`, `landed_cost_adjustment_events`.
- New invoice item immutable snapshot columns added with safe legacy defaults.

## Server contract
Apply `docs/sql/v247_invoice_inventory_costing.sql` before enabling v247 invoice-line sync against Supabase.
