#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }
HOST="feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt"
SALE="feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewSaleInvoiceEditor349.kt"
PURCHASE="feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceEditor369.kt"
DRAFT="feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/model/InvoiceDraftModels.kt"
FORM="feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorFormState.kt"
ENTITY="data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceDraftEntities.kt"
BRIDGE="app/src/main/kotlin/com/verto/app/feature/payment/bridge/PaymentPresentationBridge.kt"
CATALOG="data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt"
MIG="data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations89To90.kt"

grep -q 'PaymentDueInstallmentDraft(result.remaining.toLegacyDouble(), settlementDueAt)' "$SALE" || fail "sale credit does not materialize contractual due installment"
grep -q 'payment_v372_sale_due_required' "$SALE" || fail "sale credit due-date validation missing"
if rg -q 'pendingDue = 0L[[:space:]]*saveInvoice\(true' "$HOST"; then fail "host clears sale due date before save"; fi
pass "sale credit save carries a valid contractual due date"

grep -q 'isInternational = editorIsInternational' "$HOST" || fail "international purchase is not using dedicated purchase editor"
grep -q 'globalSupplierOnly = isInternational' "$PURCHASE" || fail "international supplier scope is not enforced"
if rg -q 'InvoiceModeBar|InvoiceItemComposer|ExtraOptionsSection' "$HOST"; then fail "legacy generic editor remains reachable"; fi
pass "local and international purchase use one modern fixed-shell editor"

grep -q 'installmentScheduleTouched' "$PURCHASE" || fail "schedule edit intent is not tracked"
grep -q '!installmentScheduleTouched && form.dueInstallments.isNotEmpty()' "$PURCHASE" || fail "existing schedule is not preserved on unrelated edit"
grep -q 'inferInstallmentIntervalMonths372' "$PURCHASE" || fail "existing interval is still reset blindly"
pass "purchase installment edits preserve contractual schedule unless explicitly changed"

grep -q 'dueInstallments: List<PaymentDueInstallmentDraft>' "$DRAFT" || fail "draft model omits installments"
grep -q 'dueInstallments = draft.dueInstallments' "$FORM" || fail "draft restore omits installments"
grep -q 'due_installments_json' "$ENTITY" || fail "Room draft storage omits installment snapshot"
grep -q 'encodeDraftDueInstallments' "$BRIDGE" || fail "draft installment encoding missing"
grep -q 'decodeDraftDueInstallments' "$BRIDGE" || fail "draft installment decoding missing"
grep -q 'ROOM_SCHEMA_VERSION: Int = 90' "$CATALOG" || fail "Room schema 90 missing"
grep -q 'Migration(89, 90)' "$MIG" || fail "89->90 migration missing"
grep -q 'val updatedAt: Long = 0L' "$DRAFT" || fail "draft content snapshot still churns timestamp"
grep -q 'initialContentSnapshot' "$HOST" || fail "edit dirty-baseline missing"
pass "drafts round-trip installment schedules and dirty detection uses a stable baseline"

for f in InvoiceEditorScreenContent.kt InvoiceEditorItemComposer.kt InvoiceEditorSections.kt; do
  test ! -e "feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/$f" || fail "dead legacy file remains: $f"
done
if rg -n 'autoAddInventoryItemForPurchase|clearSaveUiState|fun String\.isSupplier\(\)' feature/payment --glob '!**/build/**'; then
  fail "dead invoice symbols remain"
fi
pass "inactive invoice legacy removed"

echo "INVOICE_372_STATIC_GATE=PASS"
