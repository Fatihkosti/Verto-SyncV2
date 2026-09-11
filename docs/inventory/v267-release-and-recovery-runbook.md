# Verto Inventory v267 — Release and Recovery Runbook

## Preconditions

- Apply `v262_inventory_atomic_sync.sql` after v257/v258 in staging, then production.
- Export/backup the affected organization before cutover.
- Run reconciliation dry-run; approve only when unexplained quarantine is zero and drift is zero.
- Confirm every supported Android build understands contract version 2 and schema 76.

## Staged rollout

1. Deploy the compatible backend with `minimum_contract_version=1` and snapshot writes still allowed.
2. Finish canonical reconciliation and retain its checksum report.
3. Enable the Android writer for internal users; monitor outbox age, duplicate ACKs, quarantine, oversell, and drift.
4. Raise `minimum_contract_version` to 2 and set `reject_snapshot_writes=true` for a limited organization cohort.
5. Expand only while drift remains zero and no unexplained quarantine exists.

## Stop conditions

- Any unexplained drift.
- Repeated quarantine without a known missing predecessor.
- Outbox age above the operational threshold.
- Cross-organization read/write evidence.
- Core query regression above 15% from the recorded device/backend baseline.

Disable new writes with the server `writes_enabled` control and the Android `InventoryWriteGate`. Do not delete or rewrite movements.

## Recovery

- Restore service by roll-forward: repair validation/RPC code, replay the same idempotency keys, and acknowledge existing outbox rows.
- For a bad business command, append a linked reversal/cost reversal; never edit or delete the original ledger row.
- Resolve quarantined reversal only after its original movement is present and validated.
- Do not database-rollback across accepted v2 movements. A backup is evidence and disaster recovery input, not a normal ledger undo mechanism.

## Evidence required for general availability

- Build and unit-test logs from the real Gradle toolchain.
- Android Room migration tests using exported schema 75 and 76.
- Postgres RPC/RLS integration tests on a disposable Supabase project.
- Multi-device offline convergence test.
- 10,000-item/200,000-movement benchmark on a representative Android device.
- Final dry-run checksum, zero drift, and zero unexplained quarantine.
