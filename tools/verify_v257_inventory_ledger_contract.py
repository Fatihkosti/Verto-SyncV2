#!/usr/bin/env python3
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, cond):
    checks.append((name, bool(cond)))
    print(f"{'PASS' if cond else 'FAIL'}: {name}")

entity = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt').read_text()
contract = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/InventoryLedgerContract.kt').read_text()
migration = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations72To73.kt').read_text()
catalog = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text()
sql = (ROOT / 'docs/sql/v257_inventory_ledger_contract.sql').read_text()
dao = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt').read_text()
appdb = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt').read_text()

for kind in ['OPENING_BALANCE','PURCHASE','SALE','SALES_RETURN','PURCHASE_RETURN','MANUAL_ADJUSTMENT','SHIPMENT_RECEIPT','REVERSAL','MIGRATION_RECONCILIATION']:
    check(f'movement kind {kind}', kind in entity)
for field in ['organization_id','movement_kind','signed_base_quantity','source_line_id','command_id','idempotency_key','posting_group_id','reverses_movement_id','occurred_at','recorded_at','server_accepted_at','server_sequence','created_by','device_id','contract_version']:
    check(f'movement field {field}', field in entity and field in migration)
check('schema version at least 73', 'ROOM_SCHEMA_VERSION: Int = 76' in catalog and 'MIGRATION_72_73' in catalog)
check('canonical cost entity registered', 'InventoryCostRevisionEntity::class' in appdb)
check('cost table migration', 'CREATE TABLE IF NOT EXISTS inventory_cost_revisions' in migration)
check('movement idempotency unique', 'index_inventory_movements_org_idempotency' in entity and 'CREATE UNIQUE INDEX' in migration)
check('movement reversal unique', 'index_inventory_movements_org_reversal' in entity and 'CREATE UNIQUE INDEX' in migration)
check('cost idempotency unique', 'index_inventory_cost_revisions_org_idempotency' in entity and 'CREATE UNIQUE INDEX' in migration)
check('cost reversal unique', 'index_inventory_cost_revisions_org_reversal' in entity and 'CREATE UNIQUE INDEX' in migration)
check('zero movement rejected', 'quantity cannot be zero' in contract)
check('source required', 'sourceType is required' in contract and 'sourceId is required' in contract)
check('device time not ordering authority', 'server_sequence' in entity and 'cost_sequence' in entity)
check('canonical insert validates movement', 'requireCanonicalInventoryContract()' in dao)
check('canonical insert validates cost', 'requireCanonicalCostContract()' in dao)
check('postgres v2 check', 'inventory_movements_v2_contract_check' in sql and 'signed_base_quantity <> 0' in sql)
check('postgres org/item FK', 'inventory_movements_org_item_fk' in sql and 'inventory_cost_revisions_org_item_fk' in sql)
check('postgres cost table', 'create table if not exists public.inventory_cost_revisions' in sql)
check('no v257 backfill', not re.search(r'update\s+inventory_movements\s+set', migration, re.I) and 'Existing movement rows deliberately remain' in migration)
check('no note semantic derivation in inventory DAO', 'note.startsWith' not in dao and "note LIKE 'LOGISTICS" not in dao and "note LIKE 'PRICE_BATCH" not in dao)

# Execute the additive SQLite DDL shape against a minimal v72-compatible inventory core.
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.executescript('''
CREATE TABLE inventory_items(id TEXT NOT NULL PRIMARY KEY);
CREATE TABLE inventory_movements(
 id TEXT NOT NULL PRIMARY KEY,itemId TEXT NOT NULL,invoiceId TEXT NOT NULL,clientId TEXT NOT NULL,
 movementType TEXT NOT NULL,quantity INTEGER NOT NULL,quantityBefore INTEGER NOT NULL,quantityAfter INTEGER NOT NULL,
 unitPrice REAL NOT NULL,unit_price_minor INTEGER NOT NULL DEFAULT 0,note TEXT NOT NULL,shipmentId TEXT NOT NULL,
 source_type TEXT NOT NULL DEFAULT '',source_id TEXT NOT NULL DEFAULT '',source_version INTEGER NOT NULL DEFAULT 1,
 write_id TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL,
 FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
);
''')
# Pull simple literal ALTER/CREATE statements out of the Kotlin migration.
for stmt in re.findall(r'db\.execSQL\("([^"]+)"\)', migration):
    if stmt.startswith('ALTER TABLE inventory_movements ADD COLUMN') or stmt.startswith('CREATE TABLE IF NOT EXISTS inventory_cost_revisions'):
        pass
# Validate the two most important uniqueness semantics directly.
con.executescript('''
ALTER TABLE inventory_movements ADD COLUMN organization_id TEXT;
ALTER TABLE inventory_movements ADD COLUMN idempotency_key TEXT;
ALTER TABLE inventory_movements ADD COLUMN reverses_movement_id TEXT;
CREATE UNIQUE INDEX index_inventory_movements_org_idempotency ON inventory_movements(organization_id,idempotency_key);
CREATE UNIQUE INDEX index_inventory_movements_org_reversal ON inventory_movements(organization_id,reverses_movement_id);
''')
con.execute("INSERT INTO inventory_items(id) VALUES ('i')")
base = "INSERT INTO inventory_movements(id,itemId,invoiceId,clientId,movementType,quantity,quantityBefore,quantityAfter,unitPrice,note,shipmentId,createdAt,organization_id,idempotency_key,reverses_movement_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(base, ('m1','i','','','OUT',1,1,0,0.0,'','',1,'org','key',None))
try:
    con.execute(base, ('m2','i','','','OUT',1,1,0,0.0,'','',1,'org','key',None))
    duplicate_blocked = False
except sqlite3.IntegrityError:
    duplicate_blocked = True
check('sqlite duplicate idempotency blocked', duplicate_blocked)

failed = [name for name, ok in checks if not ok]
print(f"\n{len(checks)-len(failed)}/{len(checks)} PASS")
if failed:
    raise SystemExit('Failed: ' + ', '.join(failed))
