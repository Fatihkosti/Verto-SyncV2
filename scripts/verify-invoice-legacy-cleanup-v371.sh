#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Re-run the accepted purchase invoice gate first.
bash scripts/verify-purchase-invoice-v370.sh >/tmp/verto-v371-purchase-gate.log

grep -q 'PURCHASE_INVOICE_370_STATIC_GATE=PASS' /tmp/verto-v371-purchase-gate.log

# Dead files must not return.
for path in \
  feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorShipmentSection.kt \
  feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/CreatePurchaseShipmentUseCase.kt \
  feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/ObserveActivePurchaseShipmentsUseCase.kt; do
  test ! -e "$path"
done

# Dead production symbols must remain absent.
if rg -n \
  'InvoiceEditorShipmentSection|PurchaseDestinationDialog|ShipmentPickerDialog|CreateShipmentDialog|activePurchaseShipments|createPurchaseShipment\(|showPurchaseDestDialog|showShipmentPicker|selectedShipmentId|selectedShipmentTitle|showCreateShipmentDialog|newShipment(Number|Origin|Destination)|SaleToggle\(|PaymentModeToggle\(|getSalesInvoicesByDate\(|getPurchaseInvoicesByDateLegacy\(' \
  app feature data core --glob '!**/build/**' >/tmp/verto-v371-dead-symbols.log; then
  echo 'DEAD_INVOICE_SYMBOLS_REMAIN'
  cat /tmp/verto-v371-dead-symbols.log
  exit 1
fi

# The governed stable cross-feature contract is intentionally retained.
test -f feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/port/PurchaseShipmentGateway.kt
rg -q 'interface PurchaseShipmentGateway' feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/port/PurchaseShipmentGateway.kt

echo 'INVOICE_LEGACY_CLEANUP_371_STATIC_GATE=PASS'
