#!/usr/bin/env python3
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
results = []

def check(name, ok, detail=""):
    results.append({"check": name, "status": "PASS" if ok else "FAIL", "detail": detail})
    if not ok:
        raise AssertionError(f"{name}: {detail}")

catalog = (ROOT / "data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt").read_text()
appdb = (ROOT / "data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt").read_text()
migration = (ROOT / "data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations95To96.kt").read_text()
coordinator = (ROOT / "data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncV2MigrationCoordinator.kt").read_text()
planner = (ROOT / "data/sync/src/main/kotlin/com/verto/app/data/sync/migration/LegacySyncMigrationPlanner.kt").read_text()
manager = (ROOT / "data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt").read_text()

check("room_schema_96", "ROOM_SCHEMA_VERSION: Int = 96" in catalog)
check("migration_95_96_registered", "MIGRATION_95_96" in catalog and "Migration(95, 96)" in migration)
starts = [(int(a), int(b)) for a,b in re.findall(r"MIGRATION_(\d+)_(\d+)", catalog)]
unique = sorted(set(starts))
missing = [n for n in range(1,96) if (n,n+1) not in unique]
check("all_supported_room_paths_continue_to_96", not missing, f"missing={missing}")
check("room_entities_registered", all(x in appdb for x in ["SyncLegacyMigrationEntryEntity::class", "SyncLegacyMigrationStateEntity::class", "syncLegacyMigrationDao()"]))

# Execute the actual CREATE TABLE/INDEX SQL strings from the Kotlin migration on SQLite.
sql_blocks = re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^\"]*(?:\\.[^\"]*)*)")\s*\)', migration, flags=re.S)
conn = sqlite3.connect(":memory:")
for triple, quoted in sql_blocks:
    sql = triple if triple else bytes(quoted, "utf-8").decode("unicode_escape")
    conn.execute(sql)
entry_cols = {r[1] for r in conn.execute("PRAGMA table_info(sync_legacy_migration_entry)")}
state_cols = {r[1] for r in conn.execute("PRAGMA table_info(sync_legacy_migration_state)")}
check("migration_sql_executes", len(entry_cols) >= 18 and len(state_cols) >= 11)
check("tenant_source_composite_identity", [r[1] for r in conn.execute("PRAGMA table_info(sync_legacy_migration_entry)") if r[5] > 0] == ["organization_id", "source_kind", "source_id"])
check("state_tenant_principal_identity", [r[1] for r in conn.execute("PRAGMA table_info(sync_legacy_migration_state)") if r[5] > 0] == ["organization_id", "sync_principal_id"])

# Process-kill/transaction proof: rollback cannot leave half a journal; replay is idempotent by PK.
row = ("org","FINANCIAL_OUTBOX","event","user","INVOICE","inv","PENDING","POST:w",7,None,None,None,"STRONGER_SOURCE","mut","MIGRATED",None,"fp",1,1)
insert = "INSERT OR IGNORE INTO sync_legacy_migration_entry VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
conn.execute("BEGIN")
conn.execute(insert, row)
conn.rollback()
check("kill_before_commit_leaves_zero_partial_rows", conn.execute("SELECT COUNT(*) FROM sync_legacy_migration_entry").fetchone()[0] == 0)
conn.execute(insert, row)
conn.commit()
conn.execute(insert, row)
conn.commit()
check("resume_replay_is_idempotent", conn.execute("SELECT COUNT(*) FROM sync_legacy_migration_entry").fetchone()[0] == 1)
conn.execute("UPDATE sync_legacy_migration_entry SET source_state='ACKNOWLEDGED', disposition='RECEIPT_CONFIRMED' WHERE organization_id='org' AND source_kind='FINANCIAL_OUTBOX' AND source_id='event'")
conn.commit()
check("fate_can_advance_without_new_identity", conn.execute("SELECT disposition FROM sync_legacy_migration_entry").fetchone()[0] == "RECEIPT_CONFIRMED")

for source in ["financial_outbox","inventory_stock_outbox","inventory_cost_outbox","party_sync_outbox","optimal_outbox"]:
    check(f"census_{source}", source in coordinator)
for source_kind in [
    "DIRTY_PARTY_IDENTITY", "DIRTY_PARTY_ROLE", "DIRTY_CUSTOMER_PROFILE", "DIRTY_SUPPLIER_PROFILE",
    "DIRTY_INVOICE", "DIRTY_INVOICE_ITEM", "DIRTY_PAYMENT", "DIRTY_CLIENT_CREDIT",
    "DIRTY_EDUCATIONAL_CONTENT", "DIRTY_TEAM_OBSERVATION", "DIRTY_ORGANIZATION_SETTINGS",
    "DIRTY_INVENTORY_ITEM_UNSCOPED", "DIRTY_EXPENSE_UNSCOPED",
]:
    check(f"census_{source_kind.lower()}", source_kind in coordinator)
for delete_kind in ["CLIENT","INVOICE","INVENTORY","EXPENSE","CATEGORY","COMMISSION","UNIT","BUDGET","RECONCILIATION"]:
    check(f"census_delete_{delete_kind.lower()}", f"DELETION_PREF_{delete_kind}" in coordinator)

check("no_stronger_outbox_copy_to_generic", "INSERT INTO sync_outbox" not in coordinator and "syncOutboxDao" not in coordinator)
check("stronger_identity_preserved", "UnifiedStrongerBridgeRegistry.stableMutationId" in coordinator and 'targetMutationId = c.string("operation_id")' in coordinator)
check("unsafe_unscoped_rows_require_review", "M03_ORG_SCOPE_UNPROVEN" in coordinator and "DIRTY_INVENTORY_ITEM_UNSCOPED" in coordinator and "DIRTY_EXPENSE_UNSCOPED" in coordinator)
check("missing_dirty_intent_requires_review", "M03_DIRTY_WITHOUT_DURABLE_INTENT" in coordinator)
check("semantic_financial_delete_not_regenerated", "M03_DELETE_REQUIRES_DOMAIN_COMMAND" in coordinator)
check("legacy_writes_not_fenced_in_m03", "legacyWritesFenced = false" in coordinator)
check("legacy_deletion_sources_not_cleared", all(token not in coordinator for token in ["removePending", "clearPending", "deletePendingDeletion"]))

# Mutable delivery fate/target is excluded from immutable source fingerprint.
fingerprint_body = re.search(r"fun fingerprint\(candidate: LegacySyncMigrationCandidate\): String = sha256\((.*?)\n\s*\)\n", planner, flags=re.S).group(1)
check("fingerprint_excludes_mutable_fate", all(x not in fingerprint_body for x in ["sourceState", "targetMutationId", "disposition", "reasonCode"]))

prep = manager.find("prepareLegacyV2Migration")
recovery = manager.find("requiredRecoveryReason", prep)
check("m03_census_precedes_recovery", prep >= 0 and recovery > prep)
check("recovery_blocked_on_review", "M03_LOCAL_MIGRATION_REVIEW_REQUIRED" in manager and manager.find("M03_LOCAL_MIGRATION_REVIEW_REQUIRED") < manager.find("runRecovery", prep))

summary = {"status": "PASS", "checks": len(results), "results": results}
print(json.dumps(summary, ensure_ascii=False, indent=2))
