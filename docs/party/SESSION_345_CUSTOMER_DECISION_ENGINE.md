# Session 345 — Customer Decision Engine

Source of truth: `Verto-v344-party-intelligence-foundation.zip`.

## Scope

345 is the second of four Party Intelligence contracts. It adds a pure, deterministic customer decision engine over invoice/payment history. No customer/supplier UI is changed in this session.

## Implemented

- Added `CustomerDecisionEngine` with three explicit credit outcomes:
  - `ALLOW_CREDIT`
  - `CASH_ONLY`
  - `REQUIRES_APPROVAL`
- Every decision carries machine-readable reasons; incomplete financial history and mixed currencies fail closed rather than auto-approving.
- Added policy inputs (`CustomerCreditPolicy`) so thresholds are not embedded in presentation code and can later be backed by organization policy.
- Added payment amount/currency provenance to Party domain mapping (`amountMinor`, `currencyCode`, `currencyKnown`) so the engine does not base credit decisions on mixed/ambiguous legacy money.
- Added current-overdue, settlement-history, late-ratio, average-payment-days and average-days-late metrics.
- Added customer-specific repurchase cadence using the median interval between purchases, replacing a fixed 90-day assumption inside the intelligence model.
- Added predicted next purchase state: `ACTIVE`, `DUE_SOON`, `DUE`, `OVERDUE`, or insufficient history.
- Added a single recommended-action signal prioritizing collection, cash-only enforcement, repurchase follow-up, or credit review.
- Wired an `ObserveCustomerDecisionUseCase` behind `PartyApplicationService`; UI/workflow exposure remains deferred.

## Safety / invariants

- No automatic credit approval with unknown payment/invoice currency provenance.
- No automatic credit approval for multi-currency customer history until an explicit policy exists.
- Severe current delinquency or severe historical late-payment ratio can force `CASH_ONLY`.
- Sparse history returns `REQUIRES_APPROVAL`; it does not invent a risk score.
- Voided and purchase invoices do not influence customer decisions.
- No database schema or server contract change in 345.

## Verification

- `PARTY_345_STATIC_GATE=PASS`
- `PARTY_344_STATIC_GATE=PASS` retained.
- legacy `PARTY_STATIC_GATE=PASS` retained.
- Pure Kotlin Customer Decision Engine compile: PASS.
- Pure runtime harness covering approval, severe delinquency, mixed currency and repurchase prediction: `V345_CUSTOMER_DECISION_HARNESS=PASS`.
- Full Gradle tests: NOT RUN — no system Gradle is installed and the wrapper requires the unavailable Gradle 8.9 distribution.

## Deferred to 346–347

- 346: supplier performance foundation/scorecard, promised delivery date and reliability/quality/price metrics.
- 347: decision-driven customer/supplier UI, sales/purchasing workflow enforcement, and persistence/exposure of organization/customer policy such as credit limits/terms.
