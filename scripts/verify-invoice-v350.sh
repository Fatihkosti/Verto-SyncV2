#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCREEN="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreen.kt"
TABS="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceDetailsTabs350.kt"
COMP="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreenComponents.kt"
VM="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceViewModel.kt"
PORT="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/port/InvoicePresentationPort.kt"
BRIDGE="$ROOT/app/src/main/kotlin/com/verto/app/feature/invoice/bridge/InvoicePresentationBridge.kt"
NAV="$ROOT/app/src/main/kotlin/com/verto/app/ui/navigation/Screen.kt"
PAYMENT="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/payment/AddPaymentScreen.kt"
ADAPTER="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/WhatsAppInvoiceMessageShareAdapter.kt"

fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

for f in "$SCREEN" "$TABS" "$COMP" "$VM" "$PORT" "$BRIDGE" "$NAV" "$PAYMENT" "$ADAPTER"; do
  test -s "$f" || fail "missing $f"
done

grep -q 'InvoiceOverview350(summary = s, client = c)' "$SCREEN" || fail "fixed overview not wired"
grep -q 'InvoiceTabs350(selected = selectedTab' "$SCREEN" || fail "tabs not wired"
grep -q 'InvoiceDetailsTab350.ITEMS' "$SCREEN" || fail "items tab missing"
grep -q 'InvoiceDetailsTab350.PAYMENTS' "$SCREEN" || fail "payments tab missing"
grep -q 'InvoiceDetailsTab350.COMMUNICATION' "$SCREEN" || fail "communication tab missing"
pass "fixed overview plus items/payments/communication tabs wired"

grep -q 'summary.payments.sortedByDescending' "$TABS" || fail "real payment history is not rendered"
grep -q 'PaymentItem(' "$TABS" || fail "payment rows not rendered"
grep -q 'Text("دفعة جزئية")' "$TABS" || fail "partial payment action missing"
grep -q 'Text("سداد كامل")' "$TABS" || fail "pay-full action missing"
grep -q '?full={full}' "$NAV" || fail "pay-full route flag missing"
grep -q 'prefillFullPayment' "$PAYMENT" || fail "pay-full prefill missing"
pass "payment tab exposes history, partial payment, and prefilled full settlement"

grep -q 'PaymentMethod.CASH to "نقد"' "$PAYMENT" || fail "cash payment method missing"
grep -q 'PaymentMethod.TRANSFER to "تحويل"' "$PAYMENT" || fail "transfer payment method missing"
grep -q 'PaymentMethod.CHECK to "شيك"' "$PAYMENT" || fail "check payment method missing"
grep -q 'vm.savePayment(invoiceId, clientId, amt, paymentMethod' "$PAYMENT" || fail "selected payment method not persisted"
if rg -q 'showThankYouDialog|سيتم تنفيذ إرسال واتساب' "$PAYMENT"; then fail "dead thank-you UI remains in add-payment screen"; fi
pass "payment method selector is live and dead thank-you placeholder was removed"

grep -q 'observeCommunicationHistory' "$PORT" || fail "communication history port missing"
grep -q 'INVOICE_COMMUNICATION_REMINDER_OPENED' "$VM" "$BRIDGE" || fail "reminder-open audit history missing"
grep -q 'INVOICE_COMMUNICATION_THANK_YOU_OPENED' "$VM" "$BRIDGE" || fail "thank-you-open audit history missing"
grep -q 'INVOICE_COMMUNICATION_INVOICE_OPENED' "$VM" "$BRIDGE" || fail "invoice-share audit history missing"
grep -q 'ليس كإثبات إرسال' "$TABS" || fail "UI does not distinguish opened from sent"
if rg -q 'COMMUNICATION_.*SENT|تم إرسال الرسالة' "$VM" "$BRIDGE" "$TABS"; then fail "unverified sent state introduced"; fi
pass "communication tab records opened actions without claiming delivery"

grep -q 'AmountFormatter.format(payload.remaining)' "$ADAPTER" || fail "thank-you remaining does not use current remaining"
if grep -q 'payload.remaining - paidAmount' "$ADAPTER"; then fail "double-subtraction bug remains in adapter"; fi
if grep -q 'summary.remaining - paidAmount' "$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceMessagePreviewBuilder.kt"; then fail "double-subtraction bug remains in preview"; fi
pass "thank-you remaining amount no longer subtracts the payment twice"

if rg -q 'fun InvoiceItemsCard|fun InvoiceStatusCard|fun InvoiceStat|fun InvoiceClientAvatar' "$COMP"; then
  fail "replaced invoice display design still exists"
fi
if rg -q 'InvoiceItemsCard\(|InvoiceStatusCard\(' "$SCREEN"; then
  fail "legacy invoice display remains reachable"
fi
pass "replaced invoice display components removed"

bash "$ROOT/scripts/verify-invoice-v349.sh" >/dev/null
pass "v349 creation contract retained"

echo "INVOICE_350_STATIC_GATE=PASS"
