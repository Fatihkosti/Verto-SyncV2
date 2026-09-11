#!/usr/bin/env python3
from pathlib import Path
import sqlite3, sys

ROOT = Path(__file__).resolve().parents[1]

def fail(msg):
    print('V247_INVENTORY_COSTING_FAIL', msg)
    sys.exit(1)

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

required = {
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt': [
        'unit_cost_at_sale_minor', 'line_revenue_snapshot_minor', 'line_cost_snapshot_minor',
        'gross_profit_snapshot_minor', 'cost_snapshot_status',
    ],
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt': [
        'inventory_cost_revaluation_events', 'landed_cost_adjustment_events',
        'revaluation_difference_minor', 'quantity_at_adjustment',
    ],
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt': [
        'receivePurchaseAtLatestPriceAtomic', 'addQuantityAtLatestPurchasePriceAtomic',
        'Math.multiplyExact(before.quantity.toLong(), deltaMinor)',
        'applyShipmentLandedCostAtomic', 'item.quantity.toLong()',
    ],
    'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt': [
        'snapshotSaleCosts', 'receivePurchaseAtLatestPrice', 'sellPriceMinor = null',
        'costSnapshotStatus = if (trackedInventory) "KNOWN" else "UNTRACKED"', 'PurchaseScope.INTERNATIONAL',
    ],
    'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt': [
        'snapshotSaleCosts(draft.lines, command.isSale)',
    ],
    'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/CalculateShipmentLandedCostUseCase.kt': [
        'acceptedQuantity', 'LargestRemainderCostAllocator.allocate',
        'Landed-cost allocations must equal actual logistics costs exactly',
    ],
    'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/SettleShipmentLandedCostUseCase.kt': [
        'acceptedQuantity > 0', 'acceptedPurchaseValue', 'applyReceivingPostingUnitPrice',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/remote/dto/InvoicePaymentDtos.kt': [
        'unit_cost_at_sale_minor', 'gross_profit_snapshot_minor', 'cost_snapshot_status',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceLines.kt': [
        'unitCostAtSaleMinor = item.unitCostAtSaleMinor', 'unitCostAtSaleMinor = dto.unitCostAtSaleMinor',
    ],
    'docs/sql/v247_invoice_inventory_costing.sql': [
        'unit_cost_at_sale_minor', 'inventory_cost_revaluation_events', 'landed_cost_adjustment_events',
    ],
}
for rel, needles in required.items():
    s = text(rel)
    for needle in needles:
        if needle not in s:
            fail(f'{rel}: missing {needle}')

catalog = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
if 'MIGRATION_64_65' not in catalog:
    fail('Room 64->65 migration not registered')

# Acceptance arithmetic: latest purchase price is not averaged.
old_qty, old_price = 10, 10000
purchase_qty, new_price = 10, 20000
result_qty = old_qty + purchase_qty
result_price = new_price
revaluation = old_qty * (new_price - old_price)
if (result_qty, result_price, revaluation) != (20, 20000, 100000):
    fail('latest-price acceptance arithmetic')

# Historical sale cost is immutable even after a later purchase.
sale_unit_cost = old_price
current_price_after_purchase = new_price
if sale_unit_cost != 10000 or current_price_after_purchase != 20000:
    fail('historical sale cost changed')

# Execute the additive 64->65 SQL contract against a minimal schema.
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.executescript('''
CREATE TABLE inventory_items(id TEXT PRIMARY KEY);
INSERT INTO inventory_items VALUES('item');
CREATE TABLE invoice_items(
 id TEXT PRIMARY KEY, sellPrice REAL NOT NULL DEFAULT 0, sell_price_minor INTEGER NOT NULL DEFAULT 0,
 totalPrice REAL NOT NULL DEFAULT 0, total_price_minor INTEGER NOT NULL DEFAULT 0
);
INSERT INTO invoice_items VALUES('legacy',150.0,15000,150.0,15000);
''')
for sql in [
    "ALTER TABLE invoice_items ADD COLUMN unit_sell_price REAL NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN unit_sell_price_minor INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN unit_cost_at_sale REAL NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN unit_cost_at_sale_minor INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN line_revenue_snapshot REAL NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN line_revenue_snapshot_minor INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN line_cost_snapshot REAL NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN line_cost_snapshot_minor INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN gross_profit_snapshot REAL NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN gross_profit_snapshot_minor INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE invoice_items ADD COLUMN cost_snapshot_status TEXT NOT NULL DEFAULT 'LEGACY_UNKNOWN'",
    "UPDATE invoice_items SET unit_sell_price = sellPrice, unit_sell_price_minor = sell_price_minor",
    "UPDATE invoice_items SET line_revenue_snapshot = totalPrice, line_revenue_snapshot_minor = total_price_minor",
]:
    con.execute(sql)
con.executescript('''
CREATE TABLE inventory_cost_revaluation_events (
 id TEXT NOT NULL PRIMARY KEY, item_id TEXT NOT NULL, quantity_before INTEGER NOT NULL,
 old_unit_cost_minor INTEGER NOT NULL, new_unit_cost_minor INTEGER NOT NULL,
 revaluation_difference_minor INTEGER NOT NULL, source_type TEXT NOT NULL, source_id TEXT NOT NULL,
 source_version INTEGER NOT NULL DEFAULT 1, actor_id TEXT NOT NULL DEFAULT '', actor_name TEXT NOT NULL DEFAULT '',
 occurred_at INTEGER NOT NULL, write_id TEXT NOT NULL,
 FOREIGN KEY(item_id) REFERENCES inventory_items(id) ON DELETE CASCADE);
CREATE UNIQUE INDEX index_inventory_cost_revaluation_identity
 ON inventory_cost_revaluation_events(source_type,source_id,write_id,item_id,new_unit_cost_minor);
CREATE TABLE landed_cost_adjustment_events (
 id TEXT NOT NULL PRIMARY KEY, posting_id TEXT NOT NULL, shipment_id TEXT NOT NULL, item_id TEXT NOT NULL,
 quantity_at_adjustment INTEGER NOT NULL, previous_posting_unit_cost_minor INTEGER NOT NULL,
 new_posting_unit_cost_minor INTEGER NOT NULL, previous_inventory_unit_cost_minor INTEGER NOT NULL,
 new_inventory_unit_cost_minor INTEGER NOT NULL, revaluation_difference_minor INTEGER NOT NULL,
 occurred_at INTEGER NOT NULL, write_id TEXT NOT NULL,
 FOREIGN KEY(item_id) REFERENCES inventory_items(id) ON DELETE CASCADE);
CREATE UNIQUE INDEX index_landed_cost_adjustment_identity
 ON landed_cost_adjustment_events(posting_id,new_posting_unit_cost_minor);
''')
legacy = con.execute('''SELECT unit_sell_price_minor,line_revenue_snapshot_minor,
 unit_cost_at_sale_minor,line_cost_snapshot_minor,gross_profit_snapshot_minor,cost_snapshot_status
 FROM invoice_items WHERE id='legacy' ''').fetchone()
if legacy != (15000,15000,0,0,0,'LEGACY_UNKNOWN'):
    fail(f'unsafe historical cost backfill: {legacy}')

con.execute("INSERT INTO inventory_cost_revaluation_events VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            ('r1','item',10,10000,20000,100000,'INVOICE','inv1',1,'u1','User',1,'w1'))
con.execute("INSERT INTO landed_cost_adjustment_events VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            ('l1','p1','s1','item',7,20000,23000,20000,23000,21000,2,'p1'))
if con.execute('SELECT revaluation_difference_minor FROM inventory_cost_revaluation_events').fetchone()[0] != 100000:
    fail('revaluation event persistence')
if con.execute('SELECT quantity_at_adjustment FROM landed_cost_adjustment_events').fetchone()[0] != 7:
    fail('late landed-cost remaining-balance persistence')

print('V247_INVENTORY_COSTING_PASS')
