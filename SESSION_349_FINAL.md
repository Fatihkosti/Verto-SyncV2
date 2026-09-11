# SESSION 349 — Sale Invoice Progressive Editor

**Source of truth:** `Verto-v348-party-intelligence-final-hardened.zip`  
**Target:** `Verto-v349-sale-invoice-progressive-editor.zip`  
**Result:** `PASS_STATIC / BUILD_BLOCKED_ENVIRONMENT`

## Implemented scope

Session 349 implements the first approved sales-invoice contract only: **creation, fast item entry, settlement derivation, and customer credit gate**. Invoice display tabs, communication, referral/commission changes, returns, and post-posting adjustment UX remain outside this session.

### New sale creation

- A new local sale opens directly on the item composer.
- Removed the upfront sale/purchase and cash/credit choice from the new-sale experience.
- Customer is optional and can be selected from the compact header.
- No customer: Save derives a fully-paid cash sale and saves immediately.
- Selected customer: Save opens a compact settlement dialog with total, paid, and remaining.
- Paid defaults to the full invoice total.
- `paid == total` derives `CASH`.
- `0 <= paid < total` derives `CREDIT` and opens the customer-account/credit-decision screen.
- `paid > total` and invalid/negative values are rejected by a pure settlement policy.

### Item-entry flow

- Inventory search remains at the top.
- Selecting an inventory item fills its sale price and focuses quantity.
- `Done` on quantity commits the line and returns focus to search.
- Only the line list scrolls inside the editor.
- Count, total, and Save remain in the fixed bottom bar.
- Existing lines have explicit Edit and Delete controls.
- Delete offers Snackbar Undo.

### Credit decision

- The existing `CustomerDecisionEngine` remains authoritative for the customer decision.
- Party decision reads now expose outstanding and overdue amounts by currency to Payment.
- The credit screen shows existing debt, new remaining amount, overdue balances, and the existing decision banner.
- Existing fail-closed server-side credit enforcement remains in the save boundary; the UI gate does not replace it.

## Legacy removal / retention evidence

New local sale creation is routed by:

```kotlin
val useV349SaleEditor = !isEditMode && !isInternational && isSale
```

The v349 editor and its supporting component file contain no `InvoiceModeBar` and therefore cannot expose the replaced sale/purchase + cash/credit design.

The old shared editor was **not blindly deleted**, because it is still a live dependency for:

- local purchase creation,
- international purchase creation,
- existing invoice edit flow.

Therefore there is no intentionally retained dead sale-create screen. The replaced sale-create route no longer reaches the old design.

## Files added

- `feature/payment/.../application/SaleInvoiceSettlementPolicy.kt`
- `feature/payment/.../presentation/adddebt/NewSaleInvoiceEditor349.kt`
- `feature/payment/.../presentation/adddebt/NewSaleInvoiceComponents349.kt`
- `feature/payment/.../SaleInvoiceSettlementPolicy349Test.kt`
- `scripts/verify-invoice-v349.sh`
- `SESSION_349_FINAL.md`

## Files changed

- `feature/payment/.../presentation/adddebt/AddDebtScreen.kt`
- `feature/payment/.../application/model/PaymentDecisionModels.kt`
- `feature/payment/src/main/res/values/strings.xml`
- `feature/party/.../PartyDecisionReadModels.kt`
- `feature/party/.../PartyDecisionReadService.kt`
- `app/.../feature/payment/bridge/PaymentPartyBridge.kt`
- `app/.../feature/payment/bridge/PaymentPresentationBridge.kt`
- `CHANGELOG.md`

## Verification

`bash scripts/verify-invoice-v349.sh`:

- PASS — new local sale route isolated to v349 editor.
- PASS — legacy upfront choices absent from v349 editor.
- PASS — search → quantity → commit → search flow present.
- PASS — delete Undo present.
- PASS — walk-in direct cash save present.
- PASS — selected-customer settlement + credit gate present.
- PASS — XML resources well formed.
- PASS — executable settlement policy checks.
- PASS — Session 348 Party gate retained.
- `INVOICE_349_STATIC_GATE=PASS`.

Focused executable policy checks covered:

- total 500 / paid 500 → fully paid.
- total 500 / paid 0 → credit.
- total 500 / paid 300 → remaining 200 / credit.
- total 500 / paid 501 → rejected.

## Build status

Gradle compilation was attempted but did not start because the wrapper requires Gradle 8.9 and the environment cannot resolve/download it:

`java.net.UnknownHostException: services.gradle.org`

No Android build PASS is claimed. This is an execution-environment block, not a source-level build result.
