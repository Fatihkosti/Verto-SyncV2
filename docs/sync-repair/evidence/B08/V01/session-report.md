# B08-V01 — V2 read / Delta / sealed Bootstrap evidence

Date: 2026-09-12
Scope: Supabase project Verto-app + execution branch `b07-b08-release-20260912`.

## Implemented
- V2 aggregate read from accepted snapshot state.
- Opaque V2 cursors bound to authenticated V2 scope.
- Delta groups with complete members, `touchedKeys`, transaction dependencies, canonical SHA-256 and measured UTF-8 bytes.
- Soft pull budget 1000 while allowing a first legal atomic group of 1001 changes.
- Hard group contract limit 2,097,152 bytes.
- Stable Bootstrap session with `highWatermark`, Delta token, expected row count, digest and exact 35-type coverage seal.
- Bootstrap pages capped at 1000 rows / 2MiB with stable stored snapshot rows.
- V2 reconciliation manifest.
- Client pull/bootstrap routes require contract version 2 and no longer call the V1 scope/bootstrap endpoints.

## Applied server migrations
- `b08_delta_bootstrap_contract_v2`
- `b08_allow_contract_v2_scopes_bootstrap`
- `b08_pull_dependency_text_fix`
- `b08_reconciliation_manifest_v2`

The dependency-text migration is a tested correction found by the first real Delta invocation; the same query succeeded after it.

## PostgreSQL acceptance actually run
- Real V2 Delta from an existing opaque cursor returned 88 changes in 4 complete groups and advanced the opaque cursor: PASS.
- Bootstrap start over the test organization produced 1,179 sealed rows, a 64-hex digest, exact 35 aggregate coverage, high-watermark and matching Delta token: PASS.
- Bootstrap paging returned 1000 rows then 179 rows, ending `snapshot_complete=true`: PASS.
- T35: one real transaction group containing 1001 synthetic changes was returned atomically even with `softLimit=1000`: PASS.
- T36 canonical sizing: canonical server representation measured exactly 2,097,152 bytes at the legal boundary and 2,097,153 one byte above it: PASS.
- T19 server DTO round-trip: INVENTORY_MOVEMENT, INVENTORY_COST_REVISION and CLIENT_CREDIT payloads were read through V2 aggregate read and Delta unchanged, including Minor values and server sequencing/timestamp fields: PASS.
- T45/T49: cross-organization and anonymous access blocked; V1 scope refused by V2 read path: PASS.

All synthetic data fixtures were wrapped in transactions and rolled back.

## B13 dependency removed
The server now supplies the B13-required Bootstrap seal fields: digest, coverage, high-watermark and Delta token. B13 can therefore be re-run against a real V2 Bootstrap instead of remaining blocked on missing server seal fields.

## Remaining acceptance dependency
Final Android/Room/Gradle acceptance is produced by CI. This report does not label a device/Room scenario PASS unless that CI run executes it.