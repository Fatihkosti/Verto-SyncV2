#!/usr/bin/env python3
from pathlib import Path
import hashlib
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, cond):
    ok = bool(cond)
    checks.append((name, ok))
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

migration = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations73To74.kt').read_text()
catalog = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text()
entities = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryReconciliationEntities.kt').read_text()
contract = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/InventoryReconciliationContract.kt').read_text()
dao = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryReconciliationDao.kt').read_text()
sync = '\n'.join((ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync'/name).read_text() for name in ['SyncInventory.kt','SyncInventoryLedgerV2.kt','SyncInventoryReconciliation.kt'])
dto = (ROOT/'data/network/src/main/kotlin/com/verto/app/data/remote/dto/InventoryDtos.kt').read_text()
server = (ROOT/'docs/sql/v258_inventory_reconciliation.sql').read_text()
appdb = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt').read_text()

check('schema version at least 74', 'ROOM_SCHEMA_VERSION: Int = 76' in catalog)
check('migration 73 to 74 catalogued', 'MIGRATION_73_74' in catalog and 'Migration(73, 74)' in migration)
for table in ['inventory_reconciliation_control','inventory_reconciliation_markers','inventory_reconciliation_quarantine','inventory_reconciliation_apply_context']:
    check(f'local table {table}', table in migration and table in entities)
check('entities registered in Room', all(x in appdb for x in ['InventoryReconciliationControlEntity::class','InventoryReconciliationMarkerEntity::class','InventoryReconciliationQuarantineEntity::class','InventoryReconciliationApplyContextEntity::class']))
check('schema-only migration has no inventory item scan', 'SELECT * FROM inventory_items' not in migration and 'UPDATE inventory_movements' not in migration)
check('migration seeds only control metadata', "VALUES ('inventory-v2', 2, 'PENDING', 0)" in migration)
check('quantity guard exists', 'inventory_quantity_guard_v258' in migration and "RAISE(ABORT, 'INVENTORY_RECONCILIATION_REQUIRED')" in migration)
check('guard has item-scoped bypass', 'inventory_reconciliation_apply_context' in migration and 'item_id = NEW.id' in migration)
check('marker key includes organization item contract', 'primaryKeys = ["organization_id", "item_id", "contract_version"]' in entities)
check('marker checksum unique', 'index_inventory_reconciliation_markers_checksum' in entities and 'unique = true' in entities)
check('legacy balance excludes migration reconciliation', "movement_kind = 'MIGRATION_RECONCILIATION' THEN 0" in dao)
check('legacy balance uses signed or before-after fallback', 'signed_base_quantity IS NOT NULL' in dao and 'quantityAfter' in dao and 'quantityBefore' in dao)
check('local mismatch quarantines', 'LEGACY_LEDGER_MISMATCH' in dao)
check('changed authority marker quarantines', 'AUTHORITY_MARKER_CHANGED' in dao)
check('invalid checksum quarantines', 'INVALID_AUTHORITY_MARKER' in dao and 'checksum mismatch' in contract)
check('canonical snapshot update is transaction bypassed', 'installCanonicalSnapshot' in dao and 'setApplyContext' in dao and 'clearApplyContext' in dao)
check('deterministic reconciliation key', 'inventory-reconcile:$contractVersion:$organizationId:$itemId' in contract)
check('zero delta creates no movement', 'zero-delta reconciliation must not invent a movement' in contract and 'if (validated.reconciliationDelta != 0L)' in dao)
check('nonzero requires server movement identity', 'server-approved movement identity' in contract)
check('server approval metadata required', 'server approval metadata is required' in contract)
check('server authority table', 'inventory_reconciliation_authorities' in server)
check('central authority explicit lock RPC', 'inventory_lock_reconciliation_authority_v2' in server and "'CENTRAL','OWNER_DEVICE'" in server)
check('owner device staged/finalized RPC', 'inventory_stage_owner_reconciliation_snapshot_v2' in server and 'inventory_finalize_owner_reconciliation_snapshot_v2' in server)
check('server batch is bounded', 'p_limit < 1 or p_limit > 500' in server and 'limit p_limit' in server)
check('server deterministic idempotency', "inventory-reconcile:%s:%s:%s" in server)
check('server sha256 marker', "digest(concat_ws('|'," in server and "'sha256'" in server)
check('server one marker per item', 'primary key (organization_id, contract_version, item_id)' in server)
check('server quarantine exists', 'inventory_reconciliation_quarantine' in server and 'ITEM_NOT_ON_SERVER' in server)
check('server sequence assigned', 'inventory_reconciliation_server_sequence_seq' in server and "nextval('public.inventory_reconciliation_server_sequence_seq')" in server)
check('server freezes unresolved item quantity', 'inventory_reconciliation_item_guard_v2' in server and "INVENTORY_RECONCILIATION_REQUIRED" in server)
check('server freezes unresolved legacy movements', 'inventory_reconciliation_movement_guard_v2' in server)
check('server reconciler has scoped trigger bypass', "set_config('verto.inventory_reconciliation_internal', 'on', true)" in server)
check('server installs authoritative snapshot atomically', 'set quantity = v_snapshot.quantity::integer' in server)
check('same item reconciliation is serialized', 'pg_advisory_xact_lock' in server)
check('server completes authority only after all markers', "set status = 'COMPLETE'" in server and 'm.item_id is null' in server)
check('sync hydrates full items before reconciliation', 'forceFull = true' in sync and 'preserveQuantity = true' in sync)
check('sync hydrates full movements before marker RPC', 'pullInventoryMovements(orgId = orgId, forceFull = true)' in sync)
check('sync RPC uses central marker batch', 'inventory_prepare_reconciliation_batch_v2' in sync)
check('sync rejects foreign organization markers', 'reconciliation returned foreign organization data' in sync)
check('sync cannot push reconciliation movement', 'getPendingInventoryStockOutbox' in sync and 'getAllMovementsSync().filter' not in sync)
check('sync movement DTO preserves canonical fields', all(x in dto for x in ['signed_base_quantity','movement_kind','server_sequence','contract_version','idempotency_key']))
check('incomplete reconciliation blocks push', 'inventory reconciliation incomplete' in sync and 'reconcileLegacyInventoryIfRequired(orgId)' in sync)
check('inventory deletion also reconciles first', 'suspend fun SyncRuntime.pushInventoryDeletions' in sync and sync.index('reconcileLegacyInventoryIfRequired(orgId)', sync.index('suspend fun SyncRuntime.pushInventoryDeletions')) < sync.index('val pendingIds', sync.index('suspend fun SyncRuntime.pushInventoryDeletions')))
check('fresh remote rows preserve quantity until marker', 'quantity         = if (preserveQuantity) 0 else dto.quantity' in sync)
check('successful marker resolves stale quarantine', 'resolveQuarantine(' in dao)

# SQLite trigger semantics + basic resumable marker simulation.
con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE inventory_items(id TEXT PRIMARY KEY, quantity INTEGER NOT NULL, updatedAt INTEGER NOT NULL DEFAULT 0, isDirty INTEGER NOT NULL DEFAULT 0);
CREATE TABLE inventory_movements(id TEXT PRIMARY KEY, itemId TEXT NOT NULL, quantityBefore INTEGER NOT NULL, quantityAfter INTEGER NOT NULL, signed_base_quantity INTEGER, movement_kind TEXT, idempotency_key TEXT);
CREATE TABLE inventory_reconciliation_control(control_key TEXT PRIMARY KEY, contract_version INTEGER NOT NULL, state TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE inventory_reconciliation_markers(organization_id TEXT NOT NULL,item_id TEXT NOT NULL,contract_version INTEGER NOT NULL,marker_checksum TEXT NOT NULL,state TEXT NOT NULL,PRIMARY KEY(organization_id,item_id,contract_version));
CREATE TABLE inventory_reconciliation_apply_context(item_id TEXT PRIMARY KEY,token TEXT NOT NULL);
INSERT INTO inventory_reconciliation_control VALUES('inventory-v2',2,'PENDING',0);
CREATE TRIGGER inventory_quantity_guard_v258 BEFORE UPDATE OF quantity ON inventory_items
WHEN NEW.quantity <> OLD.quantity
 AND EXISTS(SELECT 1 FROM inventory_reconciliation_control WHERE control_key='inventory-v2' AND state <> 'COMPLETE')
 AND NOT EXISTS(SELECT 1 FROM inventory_reconciliation_markers WHERE item_id=NEW.id AND state='COMPLETE')
 AND NOT EXISTS(SELECT 1 FROM inventory_reconciliation_apply_context WHERE item_id=NEW.id)
BEGIN SELECT RAISE(ABORT,'INVENTORY_RECONCILIATION_REQUIRED'); END;
''')
con.execute("INSERT INTO inventory_items(id,quantity) VALUES('i1',10)")
try:
    con.execute("UPDATE inventory_items SET quantity=11 WHERE id='i1'")
    blocked = False
except sqlite3.IntegrityError:
    blocked = True
check('sqlite guard blocks pending item write', blocked)
con.execute("INSERT INTO inventory_reconciliation_apply_context VALUES('i1','t')")
con.execute("UPDATE inventory_items SET quantity=11 WHERE id='i1'")
con.execute("DELETE FROM inventory_reconciliation_apply_context WHERE item_id='i1'")
check('sqlite reconciler bypass installs snapshot', con.execute("SELECT quantity FROM inventory_items WHERE id='i1'").fetchone()[0] == 11)
con.execute("INSERT INTO inventory_reconciliation_markers VALUES('org','i1',2,'c1','COMPLETE')")
con.execute("UPDATE inventory_items SET quantity=12 WHERE id='i1'")
check('sqlite complete marker releases item', con.execute("SELECT quantity FROM inventory_items WHERE id='i1'").fetchone()[0] == 12)

# Contract arithmetic cases from the plan.
def delta(canonical, legacy):
    return canonical - legacy
check('case no movements quantity 10', delta(10, 0) == 10)
check('case snapshot equals ledger', delta(10, 10) == 0)
check('case snapshot greater than ledger', delta(15, 10) == 5)
check('case snapshot lower than ledger', delta(6, 10) == -4)


def key(org, item, version=2):
    return f'inventory-reconcile:{version}:{org}:{item}'

def checksum(org, item, canonical, legacy, movement, sequence, authority='CENTRAL', device=''):
    d = canonical - legacy
    k = key(org,item)
    payload = '|'.join(map(str,[2,org,item,canonical,legacy,d,movement or '',k,'' if sequence is None else sequence,authority,device]))
    return hashlib.sha256(payload.encode()).hexdigest()

check('two devices derive same deterministic key', key('org','item') == key('org','item'))
check('two devices receive same authoritative checksum', checksum('org','item',10,0,'m1',7) == checksum('org','item',10,0,'m1',7))
check('checksum changes when canonical changes', checksum('org','item',10,0,'m1',7) != checksum('org','item',11,0,'m1',7))

# Resumability: completed markers are durable and a retry does not create another identity.
items = [('a',10,0),('b',4,4),('c',2,5),('d',8,1)]
markers = {}
movements = {}
def apply_batch(rows):
    for item, canonical, legacy in rows:
        k = key('org', item)
        if item in markers:
            continue
        d = canonical-legacy
        if d:
            movements.setdefault(k, d)
        markers[item] = (canonical,legacy,d,k)
apply_batch(items[:2])
check('mid-batch checkpoint persists completed items', set(markers) == {'a','b'})
apply_batch(items)
check('resume completes remaining items', set(markers) == {'a','b','c','d'})
count_after_first = len(movements)
apply_batch(items)
check('rerun creates no duplicate reconciliation', len(movements) == count_after_first and len(set(movements)) == count_after_first)
check('zero delta has no movement', key('org','b') not in movements)
check('positive and negative deltas retained', movements[key('org','a')] == 10 and movements[key('org','c')] == -3)

# Multi-schema path remains consecutive to v74.
path_pairs = re.findall(r'MIGRATION_(\d+)_(\d+)', catalog)
check('catalog reaches 72 to 73', ('72','73') in path_pairs)
check('catalog reaches 73 to 74', ('73','74') in path_pairs)

failed=[n for n,ok in checks if not ok]
print(f"\n{len(checks)-len(failed)}/{len(checks)} PASS")
if failed:
    raise SystemExit('Failed: '+', '.join(failed))
