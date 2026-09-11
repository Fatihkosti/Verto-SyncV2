package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verto.app.money.Money

/** Additive, local-only sync-repair persistence. It never performs network I/O or changes delivery state. */
val MIGRATION_96_97 = object : Migration(96, 97) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_entity_version (
            organization_id TEXT NOT NULL, scope_id TEXT NOT NULL, version_family TEXT NOT NULL,
            aggregate_id TEXT NOT NULL, applied_server_version INTEGER, observed_server_version INTEGER,
            last_applied_revision INTEGER, applied_content_hash TEXT, tombstone INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,scope_id,version_family,aggregate_id),
            CHECK(tombstone IN (0,1)), CHECK(applied_server_version IS NULL OR applied_server_version>=0),
            CHECK(observed_server_version IS NULL OR observed_server_version>=0),
            CHECK(last_applied_revision IS NULL OR last_applied_revision>0),
            CHECK(applied_content_hash IS NULL OR (length(applied_content_hash)=64 AND lower(applied_content_hash)=applied_content_hash))
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_entity_version_lookup ON sync_entity_version (organization_id,version_family,aggregate_id)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_local_generation (
            organization_id TEXT NOT NULL, aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL,
            generation INTEGER NOT NULL, content_hash TEXT NOT NULL, updated_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,aggregate_type,aggregate_id),
            CHECK(generation>=0), CHECK(length(content_hash)=64 AND lower(content_hash)=content_hash)
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_mutation_packet (
            organization_id TEXT NOT NULL, mutation_id TEXT NOT NULL, source_owner TEXT NOT NULL,
            source_id TEXT NOT NULL, business_identity TEXT NOT NULL, intent_json TEXT NOT NULL,
            intent_hash TEXT NOT NULL, captured_generation INTEGER NOT NULL,
            captured_content_hash TEXT NOT NULL, version_family TEXT NOT NULL,
            initial_base_version INTEGER, predecessor_mutation_id TEXT, batch_id TEXT, wire_json TEXT,
            wire_sha256 TEXT, prepared_at INTEGER, first_dispatch_at INTEGER, created_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,mutation_id),
            CHECK(length(intent_hash)=64 AND lower(intent_hash)=intent_hash),
            CHECK(captured_generation>=0),
            CHECK(length(captured_content_hash)=64 AND lower(captured_content_hash)=captured_content_hash),
            CHECK(initial_base_version IS NULL OR initial_base_version>=0),
            CHECK((wire_json IS NULL AND wire_sha256 IS NULL AND prepared_at IS NULL) OR
                  (wire_json IS NOT NULL AND length(wire_sha256)=64 AND lower(wire_sha256)=wire_sha256 AND prepared_at IS NOT NULL)),
            CHECK(first_dispatch_at IS NULL OR prepared_at IS NOT NULL)
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_mutation_packet_source ON sync_mutation_packet (organization_id,source_owner,source_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_mutation_packet_batch ON sync_mutation_packet (batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_mutation_packet_predecessor ON sync_mutation_packet (predecessor_mutation_id)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_pending_reference (
            organization_id TEXT NOT NULL, source_owner TEXT NOT NULL, source_id TEXT NOT NULL,
            protected_type TEXT NOT NULL, protected_id TEXT NOT NULL, captured_generation INTEGER NOT NULL,
            captured_content_hash TEXT NOT NULL, dependency_kind TEXT NOT NULL,
            PRIMARY KEY (organization_id,source_owner,source_id,protected_type,protected_id),
            CHECK(captured_generation>=0),
            CHECK(length(captured_content_hash)=64 AND lower(captured_content_hash)=captured_content_hash)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_pending_reference_protected ON sync_pending_reference (organization_id,protected_type,protected_id)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_write_batch (
            organization_id TEXT NOT NULL, batch_id TEXT NOT NULL, member_count INTEGER NOT NULL,
            manifest_sha256 TEXT NOT NULL, wire_json TEXT, wire_sha256 TEXT, prepared_at INTEGER,
            sealed_at INTEGER NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,batch_id), CHECK(member_count>0),
            CHECK(length(manifest_sha256)=64 AND lower(manifest_sha256)=manifest_sha256),
            CHECK((wire_json IS NULL AND wire_sha256 IS NULL AND prepared_at IS NULL) OR
                  (wire_json IS NOT NULL AND length(wire_sha256)=64 AND lower(wire_sha256)=wire_sha256 AND prepared_at IS NOT NULL))
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_write_batch_created ON sync_write_batch (organization_id,created_at)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_write_batch_member (
            organization_id TEXT NOT NULL, batch_id TEXT NOT NULL, member_order INTEGER NOT NULL,
            mutation_id TEXT NOT NULL, source_owner TEXT NOT NULL, source_id TEXT NOT NULL,
            PRIMARY KEY (organization_id,batch_id,member_order), CHECK(member_order>=0)
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_write_batch_member_source ON sync_write_batch_member (organization_id,source_owner,source_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_write_batch_member_mutation ON sync_write_batch_member (organization_id,mutation_id)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_inbox_group (
            scope_id TEXT NOT NULL, transaction_id TEXT NOT NULL, organization_id TEXT NOT NULL,
            state TEXT NOT NULL, member_count INTEGER NOT NULL, first_revision INTEGER NOT NULL,
            last_revision INTEGER NOT NULL, manifest_sha256 TEXT NOT NULL, touched_keys_json TEXT NOT NULL,
            dependency_transaction_ids_json TEXT NOT NULL, wait_reason TEXT, created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL, applied_at INTEGER, PRIMARY KEY (scope_id,transaction_id),
            CHECK(state IN ('RECEIVED','READY','APPLIED','WAITING_LOCAL','WAITING_DEPENDENCY','REQUIRES_REVIEW')),
            CHECK(member_count>0), CHECK(first_revision>0), CHECK(last_revision>=first_revision),
            CHECK(length(manifest_sha256)=64 AND lower(manifest_sha256)=manifest_sha256),
            CHECK((state='APPLIED' AND applied_at IS NOT NULL) OR (state<>'APPLIED' AND applied_at IS NULL))
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_inbox_group_state ON sync_inbox_group (scope_id,state,first_revision)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_migration_evidence_v2 (
            organization_id TEXT NOT NULL, source_kind TEXT NOT NULL, source_id TEXT NOT NULL,
            source_content_hash TEXT NOT NULL, repair_version INTEGER NOT NULL, raw_type TEXT NOT NULL,
            operation_type TEXT NOT NULL, source_state TEXT NOT NULL, legacy_record_reference TEXT,
            classification TEXT NOT NULL, evidence_type TEXT NOT NULL, target_mutation_id TEXT,
            target_batch_id TEXT, receipt_hash TEXT, disposition TEXT NOT NULL, reason_code TEXT,
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,source_kind,source_id,source_content_hash,repair_version),
            CHECK(length(source_content_hash)=64 AND lower(source_content_hash)=source_content_hash),
            CHECK(repair_version>0),
            CHECK(receipt_hash IS NULL OR (length(receipt_hash)=64 AND lower(receipt_hash)=receipt_hash))
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_migration_evidence_v2_disposition ON sync_migration_evidence_v2 (organization_id,disposition,source_kind)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_migration_evidence_v2_mutation ON sync_migration_evidence_v2 (target_mutation_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_migration_evidence_v2_batch ON sync_migration_evidence_v2 (target_batch_id)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_snapshot_blob (
            content_hash TEXT NOT NULL PRIMARY KEY, snapshot_json TEXT NOT NULL, created_at INTEGER NOT NULL,
            CHECK(length(content_hash)=64 AND lower(content_hash)=content_hash)
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS expense_revision_history (
            organization_id TEXT NOT NULL, expense_id TEXT NOT NULL, server_version INTEGER NOT NULL,
            previous_version INTEGER, write_id TEXT NOT NULL, before_content_hash TEXT,
            after_content_hash TEXT NOT NULL, actor_id TEXT NOT NULL, changed_at INTEGER NOT NULL,
            PRIMARY KEY (organization_id,expense_id,server_version), CHECK(server_version>=0),
            CHECK(previous_version IS NULL OR previous_version>=0),
            CHECK(before_content_hash IS NULL OR (length(before_content_hash)=64 AND lower(before_content_hash)=before_content_hash)),
            CHECK(length(after_content_hash)=64 AND lower(after_content_hash)=after_content_hash)
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expense_revision_history_version ON expense_revision_history (organization_id,expense_id,server_version)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expense_revision_history_write ON expense_revision_history (organization_id,write_id)")

        addColumn(db, "sync_cursor", "received_cursor_token", "TEXT NOT NULL DEFAULT ''")
        addColumn(db, "sync_cursor", "received_high_watermark", "INTEGER")
        addColumn(db, "sync_cursor", "applied_checkpoint", "INTEGER")
        db.execSQL("""UPDATE sync_cursor SET received_cursor_token=cursor_token,
            received_high_watermark=page_high_watermark, applied_checkpoint=last_applied_change_revision""")

        addColumn(db, "sync_attachment_transfer", "next_attempt_at", "INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "sync_attachment_transfer", "last_error_code", "TEXT")
        addColumn(db, "sync_attachment_transfer", "remote_checksum", "TEXT")
        addColumn(db, "sync_attachment_transfer", "remote_byte_size", "INTEGER")
        addColumn(db, "sync_attachment_transfer", "remote_verified_at", "INTEGER")
        addColumn(db, "sync_attachment_transfer", "metadata_mutation_id", "TEXT")
        addColumn(db, "sync_attachment_transfer", "cancel_reason", "TEXT")

        addMinorColumn(db, "cash_reconciliation_sessions", "opening_balance_minor", "openingBalance")
        addMinorColumn(db, "cash_reconciliation_sessions", "total_sales_minor", "totalSales")
        addMinorColumn(db, "cash_reconciliation_sessions", "total_refunds_minor", "totalRefunds")
        addMinorColumn(db, "cash_reconciliation_sessions", "total_cash_in_minor", "totalCashIn")
        addMinorColumn(db, "cash_reconciliation_sessions", "total_cash_out_minor", "totalCashOut")
        addMinorColumn(db, "cash_reconciliation_sessions", "expected_balance_minor", "expectedBalance")
        addMinorColumn(db, "cash_reconciliation_sessions", "actual_counted_balance_minor", "actualCountedBalance")
        addMinorColumn(db, "cash_reconciliation_sessions", "variance_minor", "variance")
        addMinorColumn(db, "cash_denominations", "denomination_value_minor", "denominationValue")
        addMinorColumn(db, "cash_denominations", "subtotal_minor", "subtotal")
    }

    private fun addColumn(db: SupportSQLiteDatabase, table: String, column: String, declaration: String) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $declaration")
    }

    private fun addMinorColumn(db: SupportSQLiteDatabase, table: String, minor: String, legacy: String) {
        addColumn(db, table, minor, "INTEGER NOT NULL DEFAULT 0")
        db.query("SELECT rowid, `$legacy` FROM `$table`").use { cursor ->
            val rowIdIndex = cursor.getColumnIndexOrThrow("rowid")
            val valueIndex = cursor.getColumnIndexOrThrow(legacy)
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(rowIdIndex)
                val amountMinor = try {
                    Money.fromLegacyDouble(cursor.getDouble(valueIndex)).amountMinor
                } catch (failure: RuntimeException) {
                    throw IllegalStateException("MONEY_MIGRATION_REVIEW_REQUIRED:$table:$rowId:$legacy", failure)
                }
                db.execSQL("UPDATE `$table` SET `$minor`=? WHERE rowid=?", arrayOf(amountMinor, rowId))
            }
        }
    }
}
