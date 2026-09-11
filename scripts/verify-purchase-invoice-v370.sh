#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

HOST="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt"
LOCAL="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceEditor369.kt"
COMP="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceComponents369.kt"
COORD="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt"
ADAPTER="$ROOT/app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV2AppAdapters.kt"
DISPLAY="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/PurchaseInvoiceScreen369.kt"
PENDING="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/pendingaction/RoomFinancialPendingActionSource.kt"
ENTITY="$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt"
MIGRATION="$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations88To89.kt"
CATALOG="$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt"
OUTBOX="$ROOT/data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt"

for f in "$HOST" "$LOCAL" "$COMP" "$COORD" "$ADAPTER" "$DISPLAY" "$PENDING" "$ENTITY" "$MIGRATION" "$CATALOG" "$OUTBOX"; do
  test -s "$f" || fail "missing $f"
done

grep -q 'isInternational = editorIsInternational' "$HOST" || fail "international purchase is not routed through dedicated purchase editor"
grep -q 'persistedInternational' "$HOST" || fail "international edit does not derive scope from persisted invoice"
if grep -q 'InvoiceModeBar(' "$HOST"; then fail "legacy generic invoice editor remains reachable"; fi
pass "local/international editor routing and international edit recovery"

grep -q 'buildPurchaseInstallments370' "$LOCAL" || fail "installment schedule builder missing"
grep -q 'PaymentDueInstallmentDraft' "$LOCAL" || fail "installment draft not wired"
grep -q 'payment_v370_installment_amount' "$COMP" || fail "installment amount UI missing"
grep -q 'payment_v370_installment_every_months' "$COMP" || fail "installment interval UI missing"
grep -q 'totalScheduled == outstandingMinor' "$COORD" || fail "domain schedule sum guard missing"
grep -q 'persistDueScheduleForCreate' "$COORD" || fail "create schedule persistence missing"
grep -q 'persistDueScheduleForEdit' "$COORD" || fail "edit schedule persistence missing"
pass "contractual installment schedule persisted with exact outstanding guard"
grep -q 'installmentFirstDueAt' feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceEditor369.kt
grep -q 'payment_v370_first_due_date' feature/payment/src/main/res/values/strings.xml
grep -q 'firstDueDate = installmentFirstDueAt' feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceEditor369.kt
pass "first installment due date is explicit and user-selected"

if grep -q 'oldInvoice.category == InvoiceCategory.SALE && command.isSale' "$COORD"; then
  fail "posted purchase edit is still sale-only"
fi
grep -q 'inventoryWriter.reverseForEdit' "$COORD" || fail "posted edit reversal missing"
grep -q 'requestedCategory = if (command.isSale) InvoiceCategory.SALE else InvoiceCategory.PURCHASE' "$COORD" || fail "posted edit category guard missing"
pass "posted purchase edits use reversal/repost path"

grep -q 'currency = transactionCurrency' "$ADAPTER" || fail "international currency not propagated"
grep -q 'exchangeRate = recognitionRate' "$ADAPTER" || fail "international recognition rate not propagated"
grep -q 'toBaseCurrencyAmount' "$ADAPTER" || fail "international unit price not converted to functional currency"
if grep -q 'currency = null' "$ADAPTER"; then fail "legacy null international currency remains"; fi
if grep -q 'exchangeRate = null' "$ADAPTER"; then fail "legacy null international exchange rate remains"; fi
pass "international invoice -> logistics boundary is currency-safe"

grep -q 'invoice_v370_international_money' "$DISPLAY" || fail "international currency facts not displayed"
grep -q 'invoice_v370_functional_value' "$DISPLAY" || fail "functional recognition value not displayed"
grep -q 'invoice_v370_due_schedule' "$DISPLAY" || fail "due schedule not displayed"
pass "purchase detail shows international money context and installments"

grep -q 'effectiveDueDate370' "$PENDING" || fail "schedule-aware pending due logic missing"
grep -q 'observeAllDueInstallments' "$PENDING" || fail "due schedule does not drive pending actions"
pass "paid installments advance the next effective due date"

grep -q 'tableName = "invoice_due_installments"' "$ENTITY" || fail "due installment Room entity missing"
grep -q 'Migration(88, 89)' "$MIGRATION" || fail "88->89 migration missing"
rg -q 'ROOM_SCHEMA_VERSION: Int = (89|9[0-9]|[1-9][0-9]{2,})' "$CATALOG" || fail "Room schema regressed below 89"
grep -q 'dueInstallmentsJson' "$OUTBOX" || fail "financial outbox omits installment snapshot"
pass "schema migration and durable financial outbox snapshot"
INBOX="data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt"
grep -q 'materializeInvoiceDueInstallments370' "$INBOX" || fail "remote installment schedule is not materialized"
grep -q 'deleteDueInstallments(event.aggregateId)' "$INBOX" || fail "remote schedule replace does not clear prior rows"
grep -q 'insertDueInstallments(rows.sortedBy' "$INBOX" || fail "remote schedule rows are not persisted"
pass "installment schedule round-trips through financial sync inbox"

echo "PURCHASE_INVOICE_370_STATIC_GATE=PASS"
