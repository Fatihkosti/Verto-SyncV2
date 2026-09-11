# Session 344 — Party Intelligence Foundation

Source of truth: `Verto-v343-autodrive-control-plane-integrated.zip`.

## Scope

344 is the first of four Party Intelligence contracts. It hardens Party data and financial metrics before customer/supplier decision engines or UI expansion.

## Implemented

- Replaced silent multi-currency dashboard aggregation with currency-separated fixed-point metrics.
- Customer historical profit now uses immutable sale cost/revenue snapshots and functional-currency recognition data; unknown legacy currency/cost history fails closed instead of publishing a misleading profit.
- Moved Party dashboard money/profit calculations out of dashboard ViewModels into a pure application calculator.
- Corrected projected CLV so the future horizon no longer algebraically collapses to historical profit.
- Added an explicit normalized Party write path to `PartyDirectoryGateway`; identity + role profiles + Outbox are committed in one Room transaction.
- Supplier create/edit now stores country/currency in `supplier_profiles`; new writes no longer overload legacy `carType` / `secondaryPhones`.
- Normalized supplier profile is authoritative on edit, with legacy fields read only as migration fallback.
- Updated the old Party static gate to accept current Room schema versions while preserving the 76→77 Party baseline.
- Added Session 344 regression tests and `scripts/verify-party-v344.sh`.

## Deferred to 345–347

- Customer credit decision / next-best-action / repurchase prediction.
- Supplier scorecard, promised delivery date and reliability metrics.
- Decision-driven customer/supplier UI and sales/purchasing workflow integration.

## Verification

- `PARTY_344_STATIC_GATE=PASS`
- legacy `PARTY_STATIC_GATE=PASS`
- pure Kotlin Party metrics compile: PASS
- pure Kotlin Reports/CLV compile: PASS
- Party 344 runtime calculation harness: PASS
- Full Gradle tests: NOT RUN — wrapper requires Gradle 8.9 download and the execution environment has no network access.
- Global Architecture Guard remains FAIL from v343 inherited baselines/debt. 344 adds no new missing data-owner record or undeclared public-API module drift after the Party manifest refresh.
