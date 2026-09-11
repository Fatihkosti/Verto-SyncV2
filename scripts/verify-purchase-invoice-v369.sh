#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EDITOR="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceEditor369.kt"
COMP="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewPurchaseInvoiceComponents369.kt"
HOST="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt"
ITEM_FIELD="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorItemComponents.kt"
DISPLAY="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/PurchaseInvoiceScreen369.kt"
INVOICE_HOST="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreen.kt"
NAV="$ROOT/app/src/main/kotlin/com/verto/app/ui/navigation/InvoiceNavGraph.kt"
POLICY="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/PurchaseInvoiceSettlementPolicy.kt"

fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }
for f in "$EDITOR" "$COMP" "$HOST" "$ITEM_FIELD" "$DISPLAY" "$INVOICE_HOST" "$NAV" "$POLICY"; do test -s "$f" || fail "missing $f"; done

grep -q '} else {' "$HOST" || fail "purchase route is not isolated to dedicated editor"
grep -q 'NewPurchaseInvoiceEditor369(' "$HOST" || fail "purchase editor not wired"
grep -q 'commissionBeneficiaryClientId = if (sale).*else null' "$HOST" || fail "purchase save still carries commission beneficiary"
grep -q 'commissionSource = if (sale).*else "NONE"' "$HOST" || fail "purchase save still carries commission source"
if grep -Eq 'referrer|commission' "$EDITOR" "$COMP"; then fail "sales-only referrer/commission leaked into purchase editor"; fi
pass "local purchase creation is isolated from sale/referrer semantics"

if grep -q 'LazyColumn' "$EDITOR"; then fail "purchase editor shell scrolls"; fi
[[ $(grep -c 'LazyColumn(' "$COMP") -eq 1 ]] || fail "purchase components must have exactly one scrolling list"
grep -q 'modifier = Modifier.weight(1f)' "$EDITOR" || fail "line list is not the flexible scrolling body"
grep -q 'Icons.Filled.CalendarMonth' "$EDITOR" || fail "calendar icon missing"
grep -q 'form.showDatePicker = true' "$EDITOR" || fail "calendar icon does not open date picker"
if grep -q 'DateUtils.formatDate' "$EDITOR"; then fail "date was rendered as a separate full row"
fi
pass "header/date/composer/footer are fixed and only line list scrolls"

[[ $(grep -c 'imeAction = ImeAction.Done' "$COMP") -ge 4 ]] || fail "Done is not wired from item/quantity/sale-price/purchase-price fields"
[[ $(grep -c 'onDone = { onCommit() }' "$COMP") -ge 4 ]] || fail "Done does not commit from every purchase input field"
grep -q 'inventory.sellPrice' "$EDITOR" || fail "latest sale price is not auto-filled"
grep -q 'inventory.buyPrice' "$EDITOR" || fail "latest purchase price is not auto-filled"
grep -q 'draftItemSellPrice' "$COMP" || fail "sale price input missing"
grep -q 'draftItemBuyPrice' "$COMP" || fail "purchase price input missing"
pass "item entry supports quantity + latest sale/purchase prices and Done-to-commit"

python - "$COMP" <<'PY'
import pathlib,re,sys
s=pathlib.Path(sys.argv[1]).read_text()
m=re.search(r'internal fun PurchaseInvoiceLines369\((.*?)\n\}',s,re.S)
if not m: raise SystemExit('FAIL: PurchaseInvoiceLines369 missing')
body=m.group(1)
if 'item.sellPrice' in body or 'draftItemSellPrice' in body:
    raise SystemExit('FAIL: sale price is rendered in committed purchase lines')
if 'item.buyPrice' not in body:
    raise SystemExit('FAIL: purchase price is not rendered in committed purchase lines')
print('PASS: committed purchase list hides sale price and renders purchase price')
PY

grep -q 's.invoice.category == InvoiceCategory.PURCHASE' "$INVOICE_HOST" || fail "purchase display dispatcher missing"
grep -q 'PurchaseInvoiceScreen369(' "$INVOICE_HOST" || fail "dedicated purchase display not wired"
grep -q 'InvoiceItemsTab350(' "$DISPLAY" || fail "purchase line details missing"
grep -q 'isSale = false' "$DISPLAY" || fail "final purchase display could expose sale-price rendering"
if grep -Eqi 'InvoiceCommunicationTab350|thank.?you|referrer|commission|التواصل مع العميل|بيع نقدي' "$DISPLAY"; then
  fail "sale/customer communication semantics leaked into purchase display"
fi
grep -q 'supplier?.name' "$DISPLAY" || fail "supplier semantics missing from purchase display"
pass "purchase display is independent and supplier/purchase specific"

grep -q 'it.clientsAddPayment || it.suppliersAddPayment' "$NAV" || fail "supplier-payment navigation permission is blocked by customer-only gate"
pass "supplier payment navigation respects supplier permission while domain authorization remains authoritative"

grep -q 'if (isSale)' "$HOST" || fail "sale editor route changed"
grep -q 'NewSaleInvoiceEditor349(' "$HOST" || fail "sale editor wiring changed"
if grep -q 'InvoiceModeBar(' "$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewSaleInvoiceEditor349.kt"; then
  fail "legacy mode bar leaked into sale editor"
fi
pass "focused sale creation contract retained"

echo "PURCHASE_INVOICE_369_STATIC_GATE=PASS"
