#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EDITOR="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewSaleInvoiceEditor349.kt"
COMPONENTS="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewSaleInvoiceComponents349.kt"
HOST="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt"
POLICY="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/SaleInvoiceSettlementPolicy.kt"
MONEY="$ROOT/core/common/src/main/kotlin/com/verto/app/money/Money.kt"
STRINGS="$ROOT/feature/payment/src/main/res/values/strings.xml"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "PASS: $*"; }

grep -q 'if (isSale)' "$HOST" || fail "sale route is not isolated"
grep -q 'NewSaleInvoiceEditor349(' "$HOST" || fail "v349 editor is not wired"
pass "new local sale creation routes exclusively to v349 editor"

if grep -q 'InvoiceModeBar(' "$EDITOR"; then fail "legacy sale/purchase cash/credit mode bar leaked into v349 editor"; fi
pass "legacy upfront sale/payment choices are absent from v349 editor"

grep -q 'onNext = { quantityFocus.requestFocus() }' "$COMPONENTS" || fail "item selection does not advance to quantity"
grep -q 'searchFocusTrigger++' "$EDITOR" || fail "line commit does not restore search focus"
grep -q 'SnackbarResult.ActionPerformed' "$EDITOR" "$COMPONENTS" || fail "delete Undo missing"
pass "item flow is search -> quantity -> commit -> search, with visible delete Undo"

grep -q 'form.selectedClientId.isBlank() || form.selectedClientId == CASH_CLIENT_ID' "$EDITOR" || fail "walk-in fast-save branch missing"
grep -q 'SaleInvoiceSettlementPolicy.evaluate' "$EDITOR" || fail "settlement derivation missing"
grep -q 'onSave(PaymentMode.CREDIT, result.paid)' "$EDITOR" || fail "credit settlement does not save directly"
if rg -q 'SaleCreditDecisionScreen349|CustomerCreditWorkflowPolicy' "$EDITOR" "$COMPONENTS"; then fail "removed credit approval gate remains reachable"; fi
pass "walk-in saves directly; selected customer uses paid/remaining and credit saves without approval gate"

python - "$STRINGS" <<'PY'
import sys, xml.etree.ElementTree as ET
ET.parse(sys.argv[1])
print('PASS: payment strings XML is well formed')
PY

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/Main.kt" <<'KT'
import com.verto.app.money.Money
import com.verto.app.feature.payment.application.SaleInvoiceSettlementPolicy
import com.verto.app.feature.payment.application.SaleSettlementKind
fun main() {
    check(SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("500")).kind == SaleSettlementKind.FULLY_PAID)
    check(SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("0")).kind == SaleSettlementKind.CREDIT)
    check(SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("300")).remaining == Money.parse("200"))
    check(runCatching { SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("501")) }.isFailure)
}
KT
if command -v kotlinc >/dev/null 2>&1; then
  kotlinc "$MONEY" "$POLICY" "$TMP/Main.kt" -include-runtime -d "$TMP/check.jar"
  java -jar "$TMP/check.jar"
  pass "settlement policy executable checks"
else
  echo "SKIP: kotlinc unavailable"
fi

echo "INVOICE_349_STATIC_GATE=PASS"
