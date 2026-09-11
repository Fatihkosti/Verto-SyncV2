# SESSION 369 — Dedicated Purchase Invoice UX

**Source of truth:** `Verto-v368.zip`  
**Target:** `Verto-v369-purchase-invoice.zip`  
**Result:** `PASS_STATIC / BUILD_BLOCKED_ENVIRONMENT`

## Implemented

- Dedicated local-purchase creation surface: `NewPurchaseInvoiceEditor369`.
- Dedicated purchase detail surface: `PurchaseInvoiceScreen369`.
- Purchase creation no longer reaches the legacy shared local editor; international purchase remains on its existing path.
- Purchase display dispatches before sale-only commission/return/communication UI.
- Supplier and calendar icon share the compact header; calendar opens the existing Material `DatePickerDialog`.
- Only the committed purchase-line list scrolls in the editor; header, composer, total and Save remain fixed.
- Item composer order: item search, quantity, sale price, purchase price.
- IME Done from all four editor inputs invokes the same line-commit boundary.
- Selecting an existing inventory item hydrates current `sellPrice` and `buyPrice`.
- Committed purchase rows render quantity × purchase price only; sale price is deliberately hidden.
- Final purchase invoice lines render purchase price only (`isSale = false`).
- Purchase save strips sale-only commission beneficiary/source.
- Purchase settlement has its own pure policy and tests; cash/credit is derived from the paid amount.
- Supplier payment navigation accepts supplier-payment permission; category-specific domain authorization remains authoritative.

## Verification

`bash scripts/verify-purchase-invoice-v369.sh` => `PURCHASE_INVOICE_369_STATIC_GATE=PASS`.

Executable fixed-point policy harness:
- 500 / 500 => fully paid.
- 500 / 300 => remaining 200 / credit.
- 500 / 0 => credit.
- 500 / 501 => rejected.

Result: `PURCHASE_SETTLEMENT_POLICY=PASS`.

Design-system scan is not globally green because v368 already contains unrelated debt, but it reports no new violation for `NewPurchaseInvoice*369` or `PurchaseInvoiceScreen369`.

## Build

Attempted:

```text
./gradlew :feature:payment:compileDebugKotlin :feature:invoice:compileDebugKotlin --offline --build-cache
```

The wrapper attempted to fetch Gradle 8.9 and failed because the environment has no network access:

```text
java.net.UnknownHostException: services.gradle.org
```

No Android compile/runtime PASS is claimed.
