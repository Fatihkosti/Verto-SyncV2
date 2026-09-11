# Verto v241 Report

## Implemented

- Rebuilt final receiving UX around one explicit choice: full or short receipt.
- Full receipt hides invoice/item details; short receipt groups shortages by source invoice and item.
- Shortage inputs start blank and validate against each line's remaining quantity.
- Final-receiving draft is persisted per shipment, including the original receiving baseline and a stable request id for process-death recovery.
- Finalization is idempotent across receipt, shortage reconciliation, landed-cost settlement, and close operations using stable sub-request ids.
- Inventory posting remains accepted-quantity-only through the existing atomic receiving transaction.
- Landed cost is settled only across accepted receiving quantities before shipment close.
- Successful final receiving now settles eligible landed cost and closes the shipment in the same operational flow.
- Direct production deletion is exposed only for untouched drafts.
- Started shipments can be operationally cancelled even after inventory posting; execution/inventory/cost history remains preserved rather than hard-deleted.
- Existing closed-shipment summary is reused after the automatic close.

## Tests / verification

- Focused Kotlin v241 receiving-rules harness PASS:
  - full receipt;
  - short receipt with blank unaffected fields;
  - accepted-only quantities;
  - missing-shortage validation;
  - shortage greater than remaining quantity;
  - process-death restoration from the pre-commit baseline.
- Added v241 unit tests for final receiving and cancellation/delete policy behavior.
- Session guard delta: `outside_allowlist=0`, modules **31**, Room **61**, no deletions.
- The guard reports the exact same six inherited v240 source-package failures when run against both v240 and v241: missing Room schema `61.json` plus five already-absent legacy shipment table declarations.
- Full Gradle build remains unavailable because `gradle/wrapper/gradle-wrapper.jar` is absent from the supplied source package.

## Architecture

- Room schema: **61 unchanged**.
- Modules: **31 unchanged**.
- No migration, dependency, settings, or build-logic changes.
- Final receiving builds on the existing atomic inventory/cost application services rather than duplicating persistence logic in UI code.
