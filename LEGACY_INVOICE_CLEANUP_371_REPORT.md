# Verto v371 — Invoice Legacy Cleanup

## Scope
Dead code cleanup only. No intentional business-flow changes to sale, local purchase, international purchase, installments, reversal/repost, inventory, or logistics costing.

## Removed
- `InvoiceEditorShipmentSection.kt` (unreachable legacy shipment UI path).
- `CreatePurchaseShipmentUseCase.kt` and `ObserveActivePurchaseShipmentsUseCase.kt` (no runtime consumers after UI cutover).
- Legacy shipment dialogs: `PurchaseDestinationDialog`, `ShipmentPickerDialog`, `CreateShipmentDialog`.
- Legacy invoice editor shipment state and ViewModel collection/create methods.
- Unused `SaleToggle` and `PaymentModeToggle` composables.
- Legacy reporting DAO queries `getSalesInvoicesByDate()` and `getPurchaseInvoicesByDateLegacy()`.
- Unused database-layer `String.isSupplier()` helper.
- Dead preview/effects parameters that no longer affect rendering.
- 15 resource strings owned only by the deleted shipment dialogs.

## Intentionally retained
`PurchaseShipmentGateway`, its DTOs, app bridge/provider, and contract evidence remain because `payment.purchase-shipment.v1` is still an ACTIVE critical cross-feature stable contract in architecture governance. Removing that contract in this cleanup would create an intentional compatibility break. Runtime invoice UI no longer depends on it.

Historical architecture snapshots and old session evidence were not rewritten.

## Verification
- `scripts/verify-purchase-invoice-v370.sh` => `PURCHASE_INVOICE_370_STATIC_GATE=PASS`.
- `scripts/verify-invoice-legacy-cleanup-v371.sh` => `INVOICE_LEGACY_CLEANUP_371_STATIC_GATE=PASS`.
- Payment `strings.xml` parses successfully.
- Removed production symbols scan => no matches.
- `payment.purchase-shipment.v1` remains `UNCHANGED` in the contract compatibility guard.
- Full contract guard still reports the same three pre-existing breaking drifts already present in v370: `invoice.presentation.v1`, `notifications.center.v1`, `party.lookup.v1`. No new contract failure was introduced by v371.
- Gradle compile could not run because Gradle 8.9 is not cached and network access to `services.gradle.org` is unavailable.

## Size impact
Across the touched source/resource files: approximately 495 lines removed net. Three production Kotlin files were deleted completely.
