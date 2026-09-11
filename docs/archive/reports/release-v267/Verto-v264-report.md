# Verto v264 — Canonical Units

Status: **IMPLEMENTED / STATIC PASS**

- Unit aliases now resolve to one base stock item.
- Added integer `quantityPerUnitBase` and movement conversion snapshots.
- Removed automatic dual-balance/unit-opening inventory creation.
- Enforced zero stock on alias creation; balance is held only by the base item.

Validation: unit invariants passed static release checks. Room migration execution remains pending.
