# Verto v253 — Execution Report

Date: 2026-08-19  
Session: **253 — PO → GRN → Supplier Invoice → Payment**

## Result

**F253 IMPLEMENTED — LOCAL VERIFICATION PASS / PRODUCTION GATE BLOCKED**

## What changed

- Added optional Purchase Orders with lines and controlled lifecycle.
- Added partial Goods Receipt / GRN with accepted/rejected quantities.
- Added Three-Way Match for ordered, received, and invoiced quantities/prices.
- Added tolerance + documented permission-gated variance override.
- Enforced supplier external invoice-reference uniqueness locally/server-side.
- Added immutable receipt-to-invoice allocations, making payable quantity depend on actual accepted receipts.
- Blocked payment beyond received quantity unless a documented authorized override exists.
- Prevented duplicate inventory posting: linked LOCAL purchases post stock at GRN; INTERNATIONAL purchases only at final shipment receipt.
- Added international PO → shipment source linkage.
- Added purchase-cycle sync DTOs/RPC ordering and server SQL guards/RLS.
- Preserved F252 purchase-return tracing through GRN-backed quantities/cost sources.

## Acceptance

| Requirement | Result |
|---|---|
| Duplicate supplier invoice number rejected | PASS |
| Partial receipt keeps remainder open | PASS |
| Over-receipt rejected | PASS |
| Quantity/price variance explicit | PASS |
| Unauthorized variance override rejected | PASS |
| Payment limited to accepted/allocated receipt | PASS |
| Later GRN unlocks additional payable without rewriting match | PASS |
| INTERNATIONAL PO/invoice does not post inventory | PASS |
| Regression gates 244–252 | PASS |

## Verification

`tools/verify_v253_purchase_cycle.py`: **PASS**.  
All forward verification tools from F244 through F253: **PASS**.

The F253 verifier also executes the exact Room 69→70 SQL trigger blocks against in-memory SQLite and models partial receiving, over-receipt, matching, payment limits, later receipts, allocation bounds, and overrides.

## Build / release status

Full Gradle compilation could not start because Gradle 8.9 is not cached and the sandbox cannot reach `services.gradle.org`. Room schemas `69.json`/`70.json` are therefore not exported here. Historical missing exported schemas `56,57,58,59,62,63,64,65,66,67` also remain an inherited release gate.

`docs/sql/v253_purchase_cycle.sql` is implemented but still requires deployment and live Supabase testing, followed by Android Room/instrumented and live retry/concurrency/E2E tests.

## Decision

Use `Verto-v253-source-of-truth.zip` as the next development Source of Truth. Do **not** interpret this as production release approval until the external build/database gates pass.
