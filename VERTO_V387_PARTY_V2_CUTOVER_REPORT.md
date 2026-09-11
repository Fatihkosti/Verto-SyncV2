# Verto v387 — Party V2 Customer Cutover

## Result
- Party V2 is the customer/supplier classification source of truth.
- `ClientEntity` no longer exists in production Kotlin.
- `clients.client_types` was dropped from Supabase.
- Android Room keeps only an inert, blank `clientType` compatibility column because minSdk 26 SQLite cannot safely drop this parent-table column without rebuilding all FK children. Runtime code has no classification accessor for it.
- Optimal, AutoDrive and Max server flows read `party_roles + customer_profiles`.
- Customer/invoice/payment/commission presentation paths use Party V2 segment projections.
- Backup format v6 explicitly preserves Party roles/customer profiles/supplier profiles, including archived roles.
- Backup v4/v5 checksum compatibility is preserved.

## Data preservation verified on Supabase
- Legacy server column count: `0`
- COMPANY customers in main organization after backfill: `2`
- AutoDrive-eligible customers after backfill: `8`
- Current server functions with a real `<alias>.client_types` column reference: `0`

## Verification
- `scripts/verify-party-v387.sh`: PASS
- Migration 92→93 test: PASS
- Migration 93→94 FK-safe semantic cutover test: PASS
- Kotlin static scan: no `ClientEntity`; no runtime `clientType/clientTypes` authority.
- Kotlin parser smoke-check of `BackupManager.kt`: no syntax/parser errors detected (dependency resolution intentionally unavailable in standalone kotlinc).

## Build status
Full Gradle compilation was attempted but could not run because the Gradle 8.9 distribution is not cached locally and this environment has no network access (`UnknownHostException: services.gradle.org`).
