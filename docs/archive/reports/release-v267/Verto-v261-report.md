# Verto v261 — Immutable Inventory Ledger

Status: **IMPLEMENTED / STATIC PASS**

- Replaced business deletion with item archiving and reactivation.
- Reversal paths append deterministic movements linked by `reversesMovementId`; originals remain immutable.
- Changed the movement/item foreign key to `NO ACTION` and limited destructive clearing to explicit backup restore.
- Added release-gate checks for archive visibility, reversal uniqueness and history preservation.

Validation: v267 static gate passed. Android migration/instrumentation execution is pending because Gradle 8.9 was unavailable in the isolated environment.
