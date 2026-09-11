# Verification — Invoice F243

## 1. Source of Truth

Input: `Verto-v242.zip`.
Output: `Verto-v243-source-of-truth.zip`.

No production Kotlin/SQL behavior was intentionally changed in session 243. This session is baseline discovery and contract freezing.

## 2. Scope

Inspected:
- invoice create/edit/void
- standalone payment/reversal
- local purchase and international purchase inventory paths
- Room transaction ownership
- cash/inventory DAOs
- normal invoice/payment sync
- integration outbox path
- invoice/payment DTOs and remote payment RPC contract
- v238–v242 lineage reports

Added documentation:
- `docs/invoice/invoice-flow-map.md`
- `docs/invoice/invoice-invariants.md`
- `docs/invoice/invoice-schema-sync-baseline.md`
- `docs/invoice/invoice-v238-merge-impact.md`
- `docs/invoice/invoice-files-allowlist-244-251.md`

Added verification tool:
- `tools/verify_v243_invoice_baseline.py`

## 3. Root-cause baseline

Confirmed blockers for later repair sessions:

1. Financial source-of-truth values remain `Double` in Room/domain.
2. Invalid invoice quantities/prices can silently fall back to `1`/`0.0` in current save pipeline.
3. Inventory item resolution can silently fall back to name matching.
4. Current local transaction owner is technically capable of spanning invoice/payment/cash/inventory/outbox because these live in one `AppDatabase`.
5. Invoice/payment audit is post-commit, so it is not yet part of the atomic financial truth.
6. Normal Verto invoice sync is dirty-flag table sync, not owner-transaction Outbox/Inbox.
7. Invoice `writeId` exists only in command/integration handling and is not a persisted local invoice idempotency constraint.
8. No DRAFT/POSTED lifecycle; current save directly creates CLOSED_CASH/CLOSED_CREDIT financial records.
9. No currency snapshot/allocation/realized FX schema in invoice/payment entities.
10. Current local purchase updates latest buy price directionally, but no inventory revaluation event or historical `unitCostAtSale` snapshot exists.
11. International purchase creation correctly skips inventory; v241/v242 logistics receiving/landed-cost work must be preserved.
12. Authoritative server invoice/payment DDL/unique constraints are not included in this archive; only client DTO/RPC contracts can be certified.

## 4. Schema / contract changes

None in session 243.

Current Room schema confirmed: 61.

## 5. Tests and verification commands

Static baseline tool:

`python3 tools/verify_v243_invoice_baseline.py`

Expected result: `V243_INVOICE_BASELINE_PASS`.

Targeted Gradle verification attempted after documentation freeze:

- `./gradlew --offline :feature:invoice:testDebugUnitTest`
- `./gradlew --offline :feature:payment:testDebugUnitTest`
- `./gradlew --offline :data:database:testDebugUnitTest`

Actual results:
- F243 static baseline guard: PASS.
- Gradle tasks: not started because Gradle 8.9 distribution is not cached and network access is unavailable in this sandbox.
- Global Kotlin static quality gate: inherited baseline FAIL; F243 changed no production Kotlin.
- Byte-integrity comparison against v242: 0 original contents changed, 0 original files missing.

## 6. Atomicity evidence

`RoomDatabaseTransactionRunner` uses `AppDatabase.withTransaction`.
Invoice and payment coordinators use that owner transaction for their local financial writes.

This proves the local single-database transaction boundary exists. It does **not** prove current behavior already meets F245, because audit/general sync event ownership and idempotency constraints remain incomplete.

## 7. v238 → v242 merge result

No rollback to v238 was performed.
Current v242 remains the base.
The v239–v242 logistics work is protected as an implementation dependency.

## 8. Remaining risks

All financial defects intentionally remain for 244–251. F243 is successful only if the map/evidence is complete enough to prevent fixing them in the wrong layer.

## 9. Decision

**PASS for discovery/baseline.**
Proceed to 244 only from the produced v243 Source of Truth.
