# SESSION 350 — Invoice Details Tabs & Collection UX

**Source of truth:** `Verto-v349-sale-invoice-progressive-editor.zip`  
**Target:** `Verto-v350-invoice-details-tabs-collections.zip`  
**Result:** `PASS_STATIC / BUILD_BLOCKED_MISSING_GRADLE_WRAPPER`

## Implemented scope

Session 350 implements the second approved sales-invoice contract: **invoice display, payment access/history, settlement entry, and communication actions/history**.

### Invoice display

- Replaced the old invoice-detail composition with:
  - fixed invoice header,
  - fixed financial summary,
  - `البنود | الدفعات | التواصل` tabs,
  - only the selected tab body scrolls.
- Financial summary exposes total, paid, remaining, invoice state, customer, date, and commission when present.
- Items are rendered only inside the items tab; users no longer have to scroll past item lines to reach collections.

### Payments

- The existing `InvoiceSummary.payments` history is now actually rendered.
- Payment rows retain method, date/time, note, amount, reversal action, and thank-you action.
- Unpaid invoices expose:
  - `دفعة جزئية`,
  - `سداد كامل`.
- `سداد كامل` navigates to the payment screen with the current remaining amount prefilled.
- Payment entry now exposes all already-supported domain methods:
  - CASH / نقد,
  - TRANSFER / تحويل,
  - CHECK / شيك.
- The selected payment method is passed to the existing payment coordinator instead of hard-coding CASH.

### Communication

- Communication is isolated in its own tab.
- Actions:
  - invoice share,
  - reminder when a balance remains,
  - thank-you for the latest valid payment.
- Communication history reuses the existing immutable audit log; no Room schema migration was added.
- Recorded communication events are intentionally named `*_OPENED`:
  - `INVOICE_COMMUNICATION_INVOICE_OPENED`,
  - `INVOICE_COMMUNICATION_REMINDER_OPENED`,
  - `INVOICE_COMMUNICATION_THANK_YOU_OPENED`.
- The UI explicitly states that opening WhatsApp is **not proof of message delivery**.

### Correctness repair

The old thank-you builder subtracted the latest payment from `remaining` even though `remaining` was already post-payment. Session 350 removes that double subtraction in both preview and WhatsApp adapter.

### Legacy removal

Removed replaced/dead invoice UX instead of retaining hidden copies:

- `InvoiceItemsCard` removed.
- `InvoiceStatusCard` removed.
- `InvoiceStat` removed.
- legacy invoice client-avatar block removed with the replaced status card.
- dead `onAddDebt` callback removed from `InvoiceScreen` and its routing plumbing.
- old AddPayment thank-you dialog removed because its “send” action explicitly did not send anything.

Reusable live components were retained:

- payment row,
- commission dialog,
- bottom actions still used for print/edit/commission/void pending Session 351 adjustment work.

## Files added

- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceDetailsTabs350.kt`
- `scripts/verify-invoice-v350.sh`
- `SESSION_350_FINAL.md`

## Key files changed

- `feature/invoice/.../presentation/invoice/InvoiceScreen.kt`
- `feature/invoice/.../presentation/invoice/InvoiceScreenComponents.kt`
- `feature/invoice/.../presentation/invoice/InvoiceViewModel.kt`
- `feature/invoice/.../presentation/invoice/InvoiceMessagePreviewBuilder.kt`
- `feature/invoice/.../application/InvoicePresentationModels.kt`
- `feature/invoice/.../application/InvoicePresentationService.kt`
- `feature/invoice/.../application/port/InvoicePresentationPort.kt`
- `feature/invoice/.../domain/repository/InvoiceMessageShareGateway.kt`
- `feature/invoice/.../data/WhatsAppInvoiceMessageShareAdapter.kt`
- `feature/payment/.../presentation/payment/AddPaymentScreen.kt`
- `app/.../feature/invoice/bridge/InvoicePresentationBridge.kt`
- invoice/payment navigation wiring.

## Verification

`bash scripts/verify-invoice-v350.sh`:

- PASS — fixed overview and three tabs wired.
- PASS — real payment history rendered.
- PASS — partial and full-settlement paths wired.
- PASS — full settlement prefill wired.
- PASS — CASH / TRANSFER / CHECK selection persists the chosen method.
- PASS — dead AddPayment thank-you placeholder removed.
- PASS — communication-open history wired through audit log.
- PASS — no unverified `SENT` state introduced.
- PASS — thank-you remaining double subtraction removed.
- PASS — replaced invoice-detail components removed.
- PASS — dead invoice-screen callback removed.
- PASS — Session 349 gate retained.
- `INVOICE_350_STATIC_GATE=PASS`.

A focused bracket/syntax-structure gate also passed for all changed Kotlin files.

A whole-repository Kotlin quality scan was attempted but exceeded the execution timeout, therefore **no whole-project quality PASS is claimed**.

## Build status

Gradle compilation was attempted, but the supplied v349 source contains `gradle/wrapper/gradle-wrapper.properties` without `gradle-wrapper.jar`:

`Could not find or load main class org.gradle.wrapper.GradleWrapperMain`

Therefore no Android compile/test PASS is claimed for Session 350. This is an input/build-environment limitation recorded explicitly rather than treated as success.
