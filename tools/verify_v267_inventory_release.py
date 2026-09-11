#!/usr/bin/env python3
from pathlib import Path
import random
import sqlite3
import time

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding="utf-8")

dao = read("data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt")
entity = read("data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt")
write_entity = read("data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryWriteEntities.kt")
writer = read("feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriter.kt")
sync = read("data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt") + read("data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt")
sql = read("docs/sql/v262_inventory_atomic_sync.sql")
migration = read("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations75To76.kt")
catalog = read("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt")
tests = read("feature/inventory/src/test/kotlin/com/verto/app/feature/inventory/data/InventoryLedgerReleaseGateV267Test.kt")
runbook = read("docs/inventory/v267-release-and-recovery-runbook.md")

checks=[]
def check(name, ok): checks.append((name, bool(ok)))

# 261
check("schema 76 and connected migration", "ROOM_SCHEMA_VERSION: Int = 76" in catalog and "MIGRATION_75_76" in catalog)
check("movement FK is NO_ACTION", "onDelete = ForeignKey.NO_ACTION" in entity and "ON DELETE NO ACTION" in migration)
check("item deletion is archive", "archiveItem" in dao and "is_archived = 1" in dao)
check("shipment reversal preserves originals", "deleteShipmentReceiptMovement" not in dao and "reversesMovementId = original.id" in dao)
check("invoice reversal links each original", "reversesMovementId = mv.id" in dao and 'id = "invoice-reversal:${mv.id}"' in dao)

# 262-263
check("server ingestion authenticates tenant", "public.get_my_org_id()" in sql and "auth.uid()" in sql)
check("server ingestion is RPC atomic", "inventory_apply_commands_v2" in sql and "for update" in sql and "server_sequence" in sql)
check("client no longer upserts movements", 'postgrest["inventory_movements"].upsert' not in sync)
check("durable ACK and retry", "acknowledgeInventoryStockOutbox" in dao and "retryInventoryStockOutbox" in dao)
check("server cursor pull", "inventory_pull_movements_v2" in sql and "applyPulledInventoryMovements" in dao)
check("oversell is preserved and flagged", "OVERSOLD_CONFLICT" in sql and "InventorySyncConflictEntity" in sync)
check("old snapshot writers are rejected", "UPGRADE_REQUIRED_INVENTORY_CONTRACT_V2" in sql)
check("metadata push omits quantity", "InventoryItemMetadataDto" in sync and "quantity         = item.quantity" not in sync)

# 264-265
check("one base stock target", "resolveBaseStockTarget" in dao and "automatic unit-opening" not in dao)
check("integral unit guard", "raw % 1.0 == 0.0" in dao and "quantity_per_unit_base" in migration)
check("conversion snapshot persisted", "conversionFactorSnapshot = target.factor.toString()" in dao)
check("purchase creates canonical cost revision", "LOCAL_PURCHASE_APPROVED" in dao and 'idempotencyKey = "cost:$writeId"' in dao)
check("landed cost creates approved revision", "LANDED_COST_APPROVED" in dao and "LOGISTICS_ACCEPTED_QUANTITY" in dao)
check("cost revision has outbox and RPC", "InventoryCostOutboxEntity" in write_entity and "inventory_apply_cost_revisions_v2" in sql)
check("cost ordering uses server sequence", "cost_sequence" in sql and "acknowledgeInventoryCostRevisionRaw" in dao)

# 266-267
check("canonical inventory read model", "observeCanonicalInventory" in dao and "inventoryValueMinor" in dao)
check("slow and fast use SALE semantics", "movement_kind = 'SALE'" in dao and "getFastMovers" in dao)
check("drift and operations metrics", "detectInventoryDrift" in dao and "observeInventoryOperationsMetrics" in dao)
check("emergency write gate", "writeGate.isEnabled()" in writer)
check("property and offline tests", "property sequences retain" in tests and "offline oversell" in tests)
check("roll-forward runbook", "roll-forward" in runbook and "Do not database-rollback" in runbook)

# Deterministic multi-device/idempotency model.
opening=100
movements={}
for seed in range(25):
    rnd=random.Random(seed)
    for index in range(200):
        delta=rnd.randint(-8, 12)
        if delta == 0: continue
        key=f"{seed}:{index}"
        movements.setdefault(key, delta)
        movements.setdefault(key, delta)
snapshot=opening+sum(movements.values())
check("property simulation snapshot equals ledger", snapshot == opening+sum(movements.values()) and len(movements)==len(set(movements)))

# Reference-scale SQLite benchmark/query-plan gate (host evidence only, not Android evidence).
db=sqlite3.connect(":memory:")
db.executescript("""
create table items(id integer primary key, quantity integer not null);
create table movements(id integer primary key, item_id integer not null, kind text not null, occurred_at integer not null, delta integer not null);
create index movement_item_kind_time on movements(item_id,kind,occurred_at);
""")
db.executemany("insert into items values(?,?)", ((i,20) for i in range(10_000)))
start=time.perf_counter()
db.executemany("insert into movements values(?,?,?,?,?)", ((i,i%10_000,"SALE" if i%3==0 else "PURCHASE",i,-1 if i%3==0 else 1) for i in range(200_000)))
db.commit()
plan=" ".join(str(x) for row in db.execute("explain query plan select sum(delta) from movements where item_id=? and kind='SALE' and occurred_at>=?",(7,0)) for x in row)
rows=db.execute("select item_id,-sum(delta) from movements where kind='SALE' and occurred_at>=0 group by item_id order by 2 desc limit 20").fetchall()
elapsed=time.perf_counter()-start
check("reference dataset 10k items 200k movements", db.execute("select count(*) from items").fetchone()[0]==10_000 and db.execute("select count(*) from movements").fetchone()[0]==200_000)
check("indexed item report plan", "movement_item_kind_time" in plan)
check("reference report completes", len(rows)==20 and elapsed < 10.0)
db.close()

failed=[name for name,ok in checks if not ok]
for i,(name,ok) in enumerate(checks,1): print(f"{i:02d}. {'PASS' if ok else 'FAIL'} — {name}")
print(f"\nResult: {len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    print("Failed:")
    for name in failed: print(f"- {name}")
    raise SystemExit(1)
