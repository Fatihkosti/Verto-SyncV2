#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
checks = []

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def expect(name, cond, detail=''):
    checks.append((name, bool(cond), detail))

catalog = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
entity = text('data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt')
dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt')
sync = text('data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt')
dto = text('data/network/src/main/kotlin/com/verto/app/data/remote/dto/InventoryDtos.kt')
writer = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt')
returns = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceReturnCoordinator.kt')
purchase = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt')
participant = text('feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/sync/InventorySyncParticipant.kt')
store = text('feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryRoomAdapters.kt')

expect('room_schema_72', 'ROOM_SCHEMA_VERSION: Int = 72' in catalog)
expect('latest_schema_export_exists', (ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/72.json').exists())
if (ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/72.json').exists():
    schema = json.loads((ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/72.json').read_text())
    tables = {e['tableName']: e for e in schema['database']['entities']}
    for table in ('inventory_items','inventory_movements','inventory_cost_revaluation_events','landed_cost_adjustment_events'):
        expect(f'schema_has_{table}', table in tables)

expect('movement_is_legacy_unsigned_quantity', 'val quantity: Int' in entity and 'signedBaseQuantity' not in entity)
expect('movement_has_before_after_snapshots', 'val quantityBefore: Int' in entity and 'val quantityAfter: Int' in entity)
expect('movement_fk_currently_cascades', 'tableName = "inventory_movements"' in entity and 'onDelete = ForeignKey.CASCADE' in entity)
expect('cost_revaluation_event_exists', 'data class InventoryCostRevaluationEventEntity' in entity)
expect('landed_cost_adjustment_event_exists', 'data class LandedCostAdjustmentEventEntity' in entity)

for method in ('deductStockAtomic','addStockAtomic','receivePurchaseAtLatestPriceAtomic','restoreSalesReturnAtomic',
               'deductPurchaseReturnAtomic','receiveShipmentStockAtomic','applyShipmentLandedCostAtomic',
               'reverseShipmentReceiptsAtomic','adjustStockAtomic','reverseInvoiceMovementsAtomic'):
    expect(f'dao_{method}', f'fun {method}(' in dao)

expect('raw_quantity_update_still_exposed', 'abstract suspend fun updateQuantity' in dao)
expect('movement_delete_by_invoice_still_exposed', 'deleteMovementsByInvoiceId' in dao)
expect('edit_deletes_legacy_movements', 'stock.deleteMovements(request.invoiceId)' in writer)
expect('local_purchase_uses_latest_price_writer', 'receivePurchaseAtLatestPrice(' in writer)
expect('sales_return_has_stock_effect', 'stock.restoreSalesReturn(' in returns)
expect('purchase_return_has_stock_effect', 'stock.deductPurchaseReturn(' in returns)
expect('local_grn_posts_inventory', 'PurchaseScope.LOCAL' in purchase and 'stock.receivePurchaseAtLatestPrice(' in purchase)
expect('international_grn_not_owned_by_purchase_cycle', 'International stock remains logistics-owned' in purchase)

expect('inventory_item_wire_contains_quantity', re.search(r'data class InventoryItemDto\([\s\S]*?val quantity: Int = 0', dto) is not None)
expect('push_items_sends_snapshot_quantity', 'quantity         = item.quantity' in sync)
expect('pull_items_applies_snapshot_quantity', 'quantity         = dto.quantity' in sync)
expect('push_movements_separate', 'fun SyncRuntime.pushInventoryMovements' in sync)
expect('pull_movements_separate', 'fun SyncRuntime.pullInventoryMovements' in sync)
expect('movement_pull_insert_only', 'insertMovementIgnore(' in sync)
expect('inventory_sync_participant_orders_item_before_movement_pull', 'runtime.pullInventoryItems(' in participant and 'runtime.pullInventoryMovements(' in participant)
expect('store_save_copies_quantity_snapshot', 'inventoryDao.updateItem(entity)' in store and 'inventoryDao.insertItem(entity)' in store)

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    print(('PASS' if ok else 'FAIL') + ' ' + name + (f' — {detail}' if detail else ''))
print(f'\n{len(checks)-len(failed)}/{len(checks)} checks passed')
if failed:
    sys.exit(1)
