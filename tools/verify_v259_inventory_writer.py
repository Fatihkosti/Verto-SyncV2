#!/usr/bin/env python3
from pathlib import Path
import re
import sqlite3
import tempfile
import threading
import uuid

ROOT = Path(__file__).resolve().parents[1]
checks=[]
def check(name, cond):
    ok=bool(cond); checks.append((name,ok)); print(f"{'PASS' if ok else 'FAIL'}: {name}")

catalog=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text()
migration=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations74To75.kt').read_text()
appdb=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt').read_text()
entities=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryWriteEntities.kt').read_text()
dao=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt').read_text()
writer=(ROOT/'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriter.kt').read_text()
adapters=(ROOT/'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryRoomAdapters.kt').read_text()
ship_recv=(ROOT/'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/RoomReceiveShipmentStockAdapter.kt').read_text()
ship_rev=(ROOT/'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/RoomReverseShipmentReceiptsAdapter.kt').read_text()
return_adapter=(ROOT/'app/src/main/kotlin/com/verto/app/feature/invoice/bridge/InvoiceReturnAppAdapters.kt').read_text()
authorization=(ROOT/'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriteAuthorization.kt').read_text()
instrumented=(ROOT/'feature/inventory/src/androidTest/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriterV259Test.kt').read_text()

check('schema version at least 75', 'ROOM_SCHEMA_VERSION: Int = 76' in catalog)
check('migration 74 to 75 catalogued', 'MIGRATION_74_75' in catalog and 'Migration(74, 75)' in migration)
for table in ['inventory_write_guards','inventory_stock_outbox']:
    check(f'v259 table {table}', table in migration and table in entities)
check('v259 entities registered in Room', all(x in appdb for x in ['InventoryWriteGuardEntity::class','InventoryStockOutboxEntity::class']))
check('guard command id unique', 'index_inventory_write_guard_command' in migration and 'UNIQUE INDEX' in migration)
check('guard idempotency key unique', 'index_inventory_write_guard_idempotency' in migration)
check('outbox movement unique', 'index_inventory_stock_outbox_movement' in migration)
check('outbox starts pending', "DEFAULT 'PENDING'" in migration and 'syncState: String = "PENDING"' in entities)
check('writer exposes required operations', all(re.search(rf'suspend fun {name}\s*\(', writer) for name in ['open','receive','issue','returnStock','adjust','reverse']))
check('writer owns outer Room transaction', 'database.withTransaction' in writer)
check('writer claims idempotency before stock block', writer.index('claimInventoryWrite') < writer.index('val value = block(actorId)'))
check('movement canonicalized before outbox', writer.index('canonicalizeMovementsForWrite') < writer.index('insertInventoryStockOutbox'))
check('outbox is inside same transaction', writer.index('return database.withTransaction') < writer.index('insertInventoryStockOutbox'))
check('manual adjustment requires reason', 'reason.isBlank()' in writer)
check('manual adjustment requires inventoryEdit permission', 'authorization.canAdjust()' in writer and 'permissionProvider.canNow { it.inventoryEdit }' in authorization)
check('manual adjustment records actor', 'actorId = actorId' in writer and 'createdBy      = actorId.ifBlank { null }' in dao)
check('opening balance exists', 'openStockAtomic' in dao and 'OPENING_BALANCE' in dao)
check('metadata insert starts at zero then opens stock', 'insertItem(entity.copy(quantity = 0))' in adapters and 'stockWriter.open(item.id, item.quantity)' in adapters)
check('metadata update preserves current quantity', 'entity.copy(quantity = current.quantity)' in adapters)
check('direct updateQuantity is protected', 'protected abstract suspend fun updateQuantity' in dao)
check('atomic sqlite add uses arithmetic update', 'SET quantity = quantity + :quantity' in dao)
check('atomic sqlite issue uses conditional arithmetic update', 'SET quantity = quantity - :quantity' in dao and 'quantity >= :quantity' in dao)
check('canonical movement gets organization', 'organization_id = :organizationId' in dao)
check('canonical movement gets deterministic idempotency', "idempotency_key = :commandId || ':' || id" in dao)
check('signed quantity is derived from committed snapshots', 'CAST(quantityAfter AS INTEGER) - CAST(quantityBefore AS INTEGER)' in dao)
check('inventory adapter routes issue through writer', 'stockWriter.issue(' in adapters and 'stockWriter.receive(' in adapters)
check('shipment receive routes through writer', 'stockWriter.receiveShipment(' in ship_recv)
check('shipment reverse routes through writer', 'stockWriter.reverseShipment(' in ship_rev)
check('instrumented concurrency/rollback/idempotency tests added', all(x in instrumented for x in ['twentyConcurrentIssues_haveNoLostUpdate_andRetryIsIdempotent','movementInsertFailure_rollsBackSnapshotAndCommandGuard','outboxFailure_rollsBackSnapshotMovementAndCommandGuard','manualAdjustment_requiresPermissionAndReason']))
check('invoice returns route through writer', 'InventoryStockWriter' in return_adapter and 'inventory.returnStock(' in return_adapter and 'inventory.deductPurchaseReturn(' in return_adapter)

# Production consumers must not call quantity-mutating DAO entry points directly.
mutators = re.compile(r'\.(' + '|'.join([
    'deductStockAtomic','addStockAtomic','receivePurchaseAtLatestPriceAtomic','restoreSalesReturnAtomic',
    'deductPurchaseReturnAtomic','receiveShipmentStockAtomic','reverseShipmentReceiptsAtomic',
    'adjustStockAtomic','reverseInvoiceMovementsAtomic','openStockAtomic','updateQuantity']) + r')\s*\(')
violations=[]
for rootname in ['app','data','feature']:
    for path in (ROOT/rootname).rglob('*.kt'):
        posix=path.as_posix()
        if '/src/test/' in posix or '/src/androidTest/' in posix:
            continue
        if path.name in {'InventoryDao.kt','InventoryStockWriter.kt'}:
            continue
        text=path.read_text(errors='ignore')
        if mutators.search(text):
            violations.append(str(path.relative_to(ROOT)))
check('no production stock mutation bypasses InventoryStockWriter', not violations)
if violations:
    print('  bypasses:', ', '.join(violations))

# SQLite contract simulation: one transaction owns guard + snapshot + movement + outbox.
fd, db_path = tempfile.mkstemp(prefix='verto-v259-', suffix='.sqlite3')
Path(db_path).unlink(missing_ok=True)
con=sqlite3.connect(db_path)
con.executescript('''
PRAGMA journal_mode=WAL;
CREATE TABLE inventory_items(id TEXT PRIMARY KEY, quantity INTEGER NOT NULL);
CREATE TABLE inventory_write_guards(org TEXT NOT NULL, command TEXT NOT NULL, PRIMARY KEY(org,command));
CREATE TABLE inventory_movements(id TEXT PRIMARY KEY, org TEXT NOT NULL, command TEXT NOT NULL, item TEXT NOT NULL, before_qty INTEGER NOT NULL, after_qty INTEGER NOT NULL);
CREATE TABLE inventory_stock_outbox(id TEXT PRIMARY KEY, org TEXT NOT NULL, command TEXT NOT NULL, movement TEXT NOT NULL UNIQUE, item TEXT NOT NULL);
INSERT INTO inventory_items VALUES('item',100);
''')
con.commit(); con.close()

def apply(command, delta, fail_after_movement=False, fail_outbox=False):
    c=sqlite3.connect(db_path, timeout=10, isolation_level=None)
    try:
        c.execute('BEGIN IMMEDIATE')
        inserted=c.execute('INSERT OR IGNORE INTO inventory_write_guards VALUES(?,?)',('org',command)).rowcount
        if inserted==0:
            c.execute('COMMIT'); return 'duplicate'
        before=c.execute("SELECT quantity FROM inventory_items WHERE id='item'").fetchone()[0]
        after=before+delta
        c.execute("UPDATE inventory_items SET quantity=? WHERE id='item'",(after,))
        mid='m:'+command
        c.execute('INSERT INTO inventory_movements VALUES(?,?,?,?,?,?)',(mid,'org',command,'item',before,after))
        if fail_after_movement:
            raise RuntimeError('forced movement failure')
        if fail_outbox:
            c.execute('INSERT INTO inventory_stock_outbox VALUES(NULL,NULL,NULL,NULL,NULL)')
        else:
            c.execute('INSERT INTO inventory_stock_outbox VALUES(?,?,?,?,?)',('o:'+command,'org',command,mid,'item'))
        c.execute('COMMIT'); return 'ok'
    except Exception:
        try: c.execute('ROLLBACK')
        except Exception: pass
        return 'failed'
    finally:
        c.close()

check('rollback after movement restores snapshot', apply('fail-mid',5,fail_after_movement=True)=='failed')
c=sqlite3.connect(db_path); q=c.execute("SELECT quantity FROM inventory_items WHERE id='item'").fetchone()[0]; m=c.execute("SELECT COUNT(*) FROM inventory_movements WHERE command='fail-mid'").fetchone()[0]; g=c.execute("SELECT COUNT(*) FROM inventory_write_guards WHERE command='fail-mid'").fetchone()[0]; c.close()
check('mid-failure leaves no guard/movement/stock delta', q==100 and m==0 and g==0)
check('outbox failure rolls whole command back', apply('fail-outbox',7,fail_outbox=True)=='failed')
c=sqlite3.connect(db_path); q=c.execute("SELECT quantity FROM inventory_items WHERE id='item'").fetchone()[0]; m=c.execute("SELECT COUNT(*) FROM inventory_movements WHERE command='fail-outbox'").fetchone()[0]; c.close()
check('outbox failure leaves no stock or movement', q==100 and m==0)

# 20 concurrent commands on one item: BEGIN IMMEDIATE serializes writers, arithmetic never loses update.
results=[]
lock=threading.Lock()
def worker(i):
    r=apply(f'c{i}',1)
    with lock: results.append(r)
threads=[threading.Thread(target=worker,args=(i,)) for i in range(20)]
for t in threads: t.start()
for t in threads: t.join()
c=sqlite3.connect(db_path)
qty=c.execute("SELECT quantity FROM inventory_items WHERE id='item'").fetchone()[0]
mov=c.execute("SELECT COUNT(*) FROM inventory_movements WHERE command LIKE 'c%'").fetchone()[0]
out=c.execute("SELECT COUNT(*) FROM inventory_stock_outbox WHERE command LIKE 'c%'").fetchone()[0]
c.close()
check('20 concurrent writes lose no update', qty==120 and mov==20 and out==20 and results.count('ok')==20)
check('retry same command is idempotent', apply('c0',1)=='duplicate')
c=sqlite3.connect(db_path); qty2=c.execute("SELECT quantity FROM inventory_items WHERE id='item'").fetchone()[0]; mov2=c.execute("SELECT COUNT(*) FROM inventory_movements WHERE command='c0'").fetchone()[0]; out2=c.execute("SELECT COUNT(*) FROM inventory_stock_outbox WHERE command='c0'").fetchone()[0]; c.close()
check('retry adds no movement/outbox/delta', qty2==120 and mov2==1 and out2==1)
Path(db_path).unlink(missing_ok=True)

failed=[n for n,ok in checks if not ok]
print(f"\n{len(checks)-len(failed)}/{len(checks)} PASS")
if failed:
    raise SystemExit('Failed: '+', '.join(failed))
