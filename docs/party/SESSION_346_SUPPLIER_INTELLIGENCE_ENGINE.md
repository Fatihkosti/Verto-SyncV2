# Session 346 — Supplier Intelligence Engine

Source of truth: `Verto-v345-customer-decision-engine.zip`.

## Scope

346 is the third of four Party Intelligence contracts. It builds a pure supplier decision engine over purchase-order, GRN, purchase-return and three-way-match evidence. Party UI/workflow enforcement remains Session 347.

## Implemented

- Added `SupplierIntelligenceEngine` with explainable supplier performance snapshots.
- Added quality / acceptance rate from immutable GRN accepted vs received quantities.
- Added accepted Fill Rate over mature orders only; open orders that are not yet due are not penalized.
- Added GRN rejection rate and purchase-return rate from `PURCHASE_RETURN_DEBIT_NOTE` facts linked back to purchase invoices/POs.
- Added weighted supplier invoice-vs-PO price variance.
- Price variance fails closed for multi-currency supplier history instead of summing incomparable money.
- Added complete-order lead time and robust lead-time variability (median absolute deviation).
- Added explicit `promisedDeliveryAt` to PO domain/Room/transport/unified payload plumbing.
- Added On-Time Delivery only when a real promised date exists. Missing promises remove the metric rather than infer a deadline.
- Added supplier score with dynamic reweighting: unavailable metrics are omitted and the remaining evidence is reweighted.
- Added machine-readable score reasons and recommended actions.
- Added item-level supplier comparison / best-supplier recommendation with minimum comparable-history requirements.
- Added two cost views:
  - GRN accepted-cost snapshot by currency.
  - matched supplier-invoice cost by currency.
- Landed Cost is intentionally not claimed as supplier-attributable because current shipment/item cost revisions are not always uniquely attributable to one PO/supplier.
- Added Party-owned `SupplierIntelligenceSource`; app composition maps Room purchase facts into Party evidence instead of coupling the decision engine to Invoice persistence types.
- Added Room 83→84 migration and supplier/promise index.
- Added `docs/sql/v346_supplier_intelligence.sql` as the server deployment SQL for `promised_delivery_at`.

## Safety / invariants

- Cancelled POs do not influence supplier performance.
- Invalid purchase evidence is excluded and marks the snapshot incomplete.
- An open order is not counted against Fill Rate until it is completed/closed or its explicit promised date has passed.
- On-Time Delivery is never calculated from an arbitrary 90-day/default deadline.
- Cross-currency monetary totals remain separated by currency.
- Multi-currency price variance is unavailable without an explicit FX policy.
- Purchase returns are derived only from purchase debit-note documents with a PO-linked original invoice.
- Supplier ranking requires a minimum comparable history; sparse evidence does not manufacture a “best supplier”.
- Landed Cost remains unavailable until exact supplier/PO attribution is guaranteed.

## Verification

- `PARTY_346_STATIC_GATE=PASS`
- `PARTY_345_STATIC_GATE=PASS`
- `PARTY_344_STATIC_GATE=PASS`
- Pure Kotlin supplier engine compile: PASS.
- Pure runtime harness covering delivery promise, price variance, missing-promise behavior and item supplier ranking:
  - `V346_SUPPLIER_INTELLIGENCE_HARNESS=PASS`
- Full Gradle tests: NOT RUN — wrapper requires Gradle 8.9 and the execution environment cannot download it (`UnknownHostException: services.gradle.org`).

## Server deployment status

`docs/sql/v346_supplier_intelligence.sql` is included but **not applied to the connected Supabase project in this session**.

Until the server column is deployed, Session 347 must not expose editing/capture of non-null promised delivery dates. Existing behavior is safe because the new command field defaults to `null`.

## Deferred to 347

- Customer decision UI.
- Supplier decision UI.
- Sales credit enforcement / manager approval workflow.
- Purchase-order supplier recommendation exposure.
- Capture/edit flow for promised delivery date after server deployment.
- Exact supplier-attributable Landed Cost only if the purchasing/logistics linkage can prove attribution.
