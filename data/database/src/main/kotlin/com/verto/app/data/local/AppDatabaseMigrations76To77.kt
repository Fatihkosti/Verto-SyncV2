package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Party/Role normalization. Legacy clients and columns remain compatibility projections. */
val MIGRATION_76_77 = object : Migration(76, 77) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS party_roles (id TEXT NOT NULL PRIMARY KEY, party_id TEXT NOT NULL, organization_id TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('CUSTOMER','SUPPLIER')), status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','ARCHIVED')), created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, archived_at INTEGER, archived_by TEXT, archive_reason TEXT, sync_revision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, deleted_at INTEGER, FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_party_roles_identity ON party_roles(organization_id,party_id,role)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_roles_party_id ON party_roles(party_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_roles_listing ON party_roles(organization_id,role,status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_roles_sync ON party_roles(dirty,sync_revision)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS customer_profiles (party_id TEXT NOT NULL PRIMARY KEY, segment TEXT NOT NULL, vehicle_information TEXT NOT NULL DEFAULT '', workshop_worker_count INTEGER, updated_at INTEGER NOT NULL, sync_revision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customer_profiles_party_id ON customer_profiles(party_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_profiles (party_id TEXT NOT NULL PRIMARY KEY, scope TEXT NOT NULL CHECK(scope IN ('LOCAL','INTERNATIONAL','UNKNOWN')), country TEXT NOT NULL DEFAULT '', currency_code TEXT NOT NULL DEFAULT '', specialty TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, sync_revision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_profiles_party_id ON supplier_profiles(party_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS party_migration_issues (id TEXT NOT NULL PRIMARY KEY, party_id TEXT NOT NULL, field TEXT NOT NULL, raw_value TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, resolved INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_migration_issues_party_id ON party_migration_issues(party_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_migration_issues_resolved ON party_migration_issues(resolved)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS party_role_audit (id TEXT NOT NULL PRIMARY KEY, party_id TEXT NOT NULL, organization_id TEXT NOT NULL, role TEXT NOT NULL, action TEXT NOT NULL, actor_id TEXT NOT NULL, reason TEXT NOT NULL, occurred_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_role_audit_party_id ON party_role_audit(party_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_role_audit_occurred_at ON party_role_audit(occurred_at)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS party_sync_outbox (id TEXT NOT NULL PRIMARY KEY, operation_id TEXT NOT NULL, aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL, base_revision INTEGER NOT NULL, payload_version INTEGER NOT NULL DEFAULT 2, payload_json TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'PENDING', retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', next_attempt_at INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_party_sync_outbox_operation_id ON party_sync_outbox(operation_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_sync_outbox_state_next_attempt_at ON party_sync_outbox(state,next_attempt_at)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS party_sync_conflicts (id TEXT NOT NULL PRIMARY KEY, aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL, local_payload TEXT NOT NULL, remote_payload TEXT NOT NULL, base_revision INTEGER NOT NULL, server_revision INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, resolved INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_sync_conflicts_aggregate_id ON party_sync_conflicts(aggregate_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_sync_conflicts_resolved ON party_sync_conflicts(resolved)")

        val tokenExpr = "(',' || UPPER(REPLACE(COALESCE(clientType,''),' ','')) || ',')"
        val customerTokens = listOf("INDIVIDUAL","COMPANY","INSTITUTION","CAR_OWNER","MECHANIC","SHOP_OWNER","WORKSHOP_OWNER","MARKETER","TRADER","DISTRIBUTOR","WHOLESALE_TRADER","COMPETITOR","OTHER")
        val customerPredicate = customerTokens.joinToString(" OR ") { "INSTR($tokenExpr, ',$it,') > 0" }
        val supplierPredicate = "INSTR($tokenExpr, ',SUPPLIER,') > 0 OR INSTR($tokenExpr, ',GLOBAL_SUPPLIER,') > 0 OR INSTR($tokenExpr, ',COMPETITOR,') > 0"
        db.execSQL("INSERT OR IGNORE INTO party_roles(id,party_id,organization_id,role,status,created_at,updated_at,dirty) SELECT id||':CUSTOMER',id,'','CUSTOMER','ACTIVE',createdAt,createdAt,1 FROM clients WHERE $customerPredicate")
        db.execSQL("INSERT OR IGNORE INTO party_roles(id,party_id,organization_id,role,status,created_at,updated_at,dirty) SELECT id||':SUPPLIER',id,'','SUPPLIER','ACTIVE',createdAt,createdAt,1 FROM clients WHERE $supplierPredicate")

        val segmentCase = customerTokens.joinToString(" ") { "WHEN INSTR($tokenExpr, ',$it,') > 0 THEN '$it'" }
        db.execSQL("INSERT OR IGNORE INTO customer_profiles(party_id,segment,vehicle_information,workshop_worker_count,updated_at,dirty) SELECT id, CASE $segmentCase ELSE 'OTHER' END, CASE WHEN INSTR($tokenExpr, ',SUPPLIER,')=0 AND INSTR($tokenExpr, ',GLOBAL_SUPPLIER,')=0 THEN COALESCE(carType,'') ELSE '' END, CASE WHEN INSTR($tokenExpr, ',WORKSHOP_OWNER,')>0 AND TRIM(secondaryPhones) NOT GLOB '*[^0-9]*' AND LENGTH(TRIM(secondaryPhones)) BETWEEN 1 AND 5 THEN CAST(TRIM(secondaryPhones) AS INTEGER) ELSE NULL END, createdAt,1 FROM clients WHERE $customerPredicate")
        db.execSQL("INSERT OR IGNORE INTO supplier_profiles(party_id,scope,country,currency_code,specialty,updated_at,dirty) SELECT id, CASE WHEN INSTR($tokenExpr, ',GLOBAL_SUPPLIER,')>0 THEN 'INTERNATIONAL' WHEN INSTR($tokenExpr, ',SUPPLIER,')>0 THEN 'LOCAL' ELSE 'UNKNOWN' END, '', CASE WHEN UPPER(TRIM(secondaryPhones)) IN ('SDG','USD','EUR','SAR','AED','EGP','CNY') THEN UPPER(TRIM(secondaryPhones)) ELSE '' END, COALESCE(specialty,''),createdAt,1 FROM clients WHERE $supplierPredicate")

        db.execSQL("INSERT OR IGNORE INTO party_migration_issues(id,party_id,field,raw_value,reason,created_at) SELECT id||':supplier-country',id,'carType',carType,'AMBIGUOUS_SUPPLIER_COUNTRY',createdAt FROM clients WHERE ($supplierPredicate) AND TRIM(COALESCE(carType,''))<>''")
        db.execSQL("INSERT OR IGNORE INTO party_migration_issues(id,party_id,field,raw_value,reason,created_at) SELECT id||':supplier-secondary',id,'secondaryPhones',secondaryPhones,'AMBIGUOUS_SUPPLIER_CURRENCY_OR_PHONE',createdAt FROM clients WHERE ($supplierPredicate) AND TRIM(COALESCE(secondaryPhones,''))<>'' AND UPPER(TRIM(secondaryPhones)) NOT IN ('SDG','USD','EUR','SAR','AED','EGP','CNY')")
        db.execSQL("INSERT OR IGNORE INTO party_migration_issues(id,party_id,field,raw_value,reason,created_at) SELECT id||':unknown-type',id,'clientType',clientType,'UNKNOWN_OR_EMPTY_LEGACY_TYPE',createdAt FROM clients WHERE NOT ($customerPredicate) AND NOT ($supplierPredicate)")
        installPartyHistoricalDeleteGuard(db)
    }
}

fun installPartyHistoricalDeleteGuard(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TRIGGER IF NOT EXISTS prevent_client_delete_with_history
        BEFORE DELETE ON clients
        WHEN EXISTS(SELECT 1 FROM invoices WHERE clientId=OLD.id)
          OR EXISTS(SELECT 1 FROM client_credits WHERE clientId=OLD.id)
          OR EXISTS(SELECT 1 FROM client_reminders WHERE clientId=OLD.id)
          OR EXISTS(SELECT 1 FROM commission_payments WHERE clientId=OLD.id)
          OR EXISTS(SELECT 1 FROM logistics_shipment_sources WHERE supplier_id=OLD.id)
        BEGIN SELECT RAISE(ABORT,'party_history_prevents_delete'); END
    """.trimIndent())
}
