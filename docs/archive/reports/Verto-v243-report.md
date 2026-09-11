# Verto v243 Report

## Implemented

- Completed the invoice financial baseline required by session 243 against v242.
- Mapped create/edit/payment/reversal/void/international-receiving/sync paths from UI/application to DAO/remote boundaries.
- Confirmed one `AppDatabase` / Room transaction owner can span local invoice, payment, cash, inventory and integration-outbox writes.
- Froze 50 financial invariants for sessions 244–251.
- Documented current schema/sync contract and v238→v242 merge impact.
- Added explicit modification allowlist for 244–251 to protect later logistics work.
- Added `tools/verify_v243_invoice_baseline.py` as a reproducible evidence guard.

## Key findings

- Room schema is 61.
- Financial Room/domain values are still `Double`; current `MoneyMath` is a precision helper, not the final fixed-point Money Core.
- Current invoice parsing contains silent quantity/price fallbacks.
- Purchase/sale inventory lookup can fall back to item name.
- Invoice/payment audit is post-commit.
- Normal Verto invoice sync is dirty-row upsert/pull; no general financial Outbox/Inbox yet.
- Optimal integration has an atomic outbox pattern, but it is not the normal Verto invoice sync transport.
- Current invoice lifecycle has `CLOSED_CASH/CLOSED_CREDIT + voided`, not `DRAFT/POSTED/VOID`.
- International purchase creation correctly avoids inventory; current logistics receiving/landed-cost work must be preserved.
- Server payment RPC exposes client request id / replay contract, but authoritative invoice/payment server DDL is absent from this source archive.

## Architecture

- Production Kotlin/SQL behavior changed: **none**.
- Room schema changed: **no** (61).
- Dependencies changed: **no**.
- v242 logistics implementation retained intact.

## Verification

Results are appended by the F243 execution step below.

### Actual execution results

- `python3 tools/verify_v243_invoice_baseline.py`: **PASS** (`V243_INVOICE_BASELINE_PASS`).
- Original v242 archive byte-integrity check after F243 additions: **PASS** — 0 original file contents changed, 0 original files missing.
- Targeted Gradle unit-test command: **NOT RUNNABLE in this sandbox**. The wrapper attempted to fetch Gradle 8.9 because no cached distribution exists; network resolution is unavailable (`UnknownHostException: services.gradle.org`). No test task started.
- `python3 scripts/verify-kotlin-quality-static.py`: **FAIL on inherited global baseline** — dependency cycles 0, but existing long-function/parameter-list/large-file/not-null ceilings are already above the script's configured thresholds. Session 243 made no production Kotlin changes, so this is recorded as inherited debt rather than an F243 regression.

## Exit decision

**F243 PASS** for its required discovery/baseline scope.

The production tree is byte-identical to v242. Proceed to 244 from this package; 244 is the first session allowed to change financial production code.
