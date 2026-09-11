package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v258: schema-only migration for resumable inventory reconciliation.
 * No per-item scan/backfill is executed here; the post-open reconciler owns all data work.
 */
val MIGRATION_73_74 = object : Migration(73, 74) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_reconciliation_control (
                control_key TEXT NOT NULL PRIMARY KEY,
                contract_version INTEGER NOT NULL,
                state TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_reconciliation_markers (
                organization_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                contract_version INTEGER NOT NULL,
                authority_kind TEXT NOT NULL,
                source_device_id TEXT,
                canonical_snapshot INTEGER NOT NULL,
                authoritative_legacy_balance INTEGER NOT NULL,
                reconciliation_delta INTEGER NOT NULL,
                reconciliation_movement_id TEXT,
                idempotency_key TEXT NOT NULL,
                server_sequence INTEGER,
                marker_checksum TEXT NOT NULL,
                state TEXT NOT NULL,
                approved_at INTEGER NOT NULL,
                server_accepted_at INTEGER NOT NULL,
                approved_by TEXT NOT NULL,
                completed_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, item_id, contract_version)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_reconciliation_markers_item ON inventory_reconciliation_markers(item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_reconciliation_markers_org_state ON inventory_reconciliation_markers(organization_id, state)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_reconciliation_markers_checksum ON inventory_reconciliation_markers(marker_checksum)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_reconciliation_quarantine (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                contract_version INTEGER NOT NULL,
                reason TEXT NOT NULL,
                local_legacy_balance INTEGER,
                authoritative_legacy_balance INTEGER,
                canonical_snapshot INTEGER,
                marker_checksum TEXT,
                detected_at INTEGER NOT NULL,
                details TEXT NOT NULL,
                resolved_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_reconciliation_quarantine_identity ON inventory_reconciliation_quarantine(organization_id, item_id, contract_version)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_reconciliation_quarantine_open ON inventory_reconciliation_quarantine(organization_id, resolved_at)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_reconciliation_apply_context (
                item_id TEXT NOT NULL PRIMARY KEY,
                token TEXT NOT NULL
            )
            """.trimIndent()
        )

        // One metadata row only. Existing items are guarded immediately; no item backfill runs here.
        db.execSQL(
            """
            INSERT OR IGNORE INTO inventory_reconciliation_control(control_key, contract_version, state, updated_at)
            VALUES ('inventory-v2', 2, 'PENDING', 0)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS inventory_quantity_guard_v258
            BEFORE UPDATE OF quantity ON inventory_items
            WHEN NEW.quantity <> OLD.quantity
             AND EXISTS (
                 SELECT 1 FROM inventory_reconciliation_control
                 WHERE control_key = 'inventory-v2' AND state <> 'COMPLETE'
             )
             AND NOT EXISTS (
                 SELECT 1 FROM inventory_reconciliation_markers
                 WHERE item_id = NEW.id AND state = 'COMPLETE'
             )
             AND NOT EXISTS (
                 SELECT 1 FROM inventory_reconciliation_apply_context
                 WHERE item_id = NEW.id
             )
            BEGIN
                SELECT RAISE(ABORT, 'INVENTORY_RECONCILIATION_REQUIRED');
            END
            """.trimIndent()
        )
    }
}
