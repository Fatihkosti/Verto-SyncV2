package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v261-v266: immutable history, archive metadata, durable sync acknowledgement and conflicts. */
val MIGRATION_75_76 = object : Migration(75, 76) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN archived_at INTEGER")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN archived_by TEXT")
        db.execSQL("ALTER TABLE inventory_units ADD COLUMN quantity_per_unit_base INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE inventory_units SET quantity_per_unit_base = CAST(quantityPerUnit AS INTEGER) " +
                "WHERE quantityPerUnit > 0 AND quantityPerUnit = CAST(quantityPerUnit AS INTEGER)"
        )
        db.execSQL("ALTER TABLE inventory_stock_outbox ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_stock_outbox ADD COLUMN last_error TEXT")
        db.execSQL("ALTER TABLE inventory_stock_outbox ADD COLUMN ack_sequence INTEGER")
        db.execSQL("ALTER TABLE inventory_stock_outbox ADD COLUMN acknowledged_at INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_sync_conflicts (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                conflict_key TEXT NOT NULL,
                item_id TEXT NOT NULL,
                conflict_type TEXT NOT NULL,
                server_sequence INTEGER NOT NULL,
                projected_quantity INTEGER NOT NULL,
                status TEXT NOT NULL,
                details TEXT NOT NULL,
                detected_at INTEGER NOT NULL,
                resolved_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_sync_conflicts_key ON inventory_sync_conflicts(organization_id, conflict_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_sync_conflicts_status ON inventory_sync_conflicts(organization_id, status, detected_at)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_sync_cursors (
                organization_id TEXT NOT NULL PRIMARY KEY,
                last_server_sequence INTEGER NOT NULL,
                last_cost_sequence INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_cost_outbox (
                id TEXT NOT NULL PRIMARY KEY, organization_id TEXT NOT NULL, command_id TEXT NOT NULL,
                cost_revision_id TEXT NOT NULL, sync_state TEXT NOT NULL, attempt_count INTEGER NOT NULL,
                next_attempt_at INTEGER NOT NULL, last_error TEXT, ack_sequence INTEGER,
                acknowledged_at INTEGER, created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX index_inventory_cost_outbox_revision ON inventory_cost_outbox(organization_id,cost_revision_id)")
        db.execSQL("CREATE INDEX index_inventory_cost_outbox_delivery ON inventory_cost_outbox(organization_id,sync_state,next_attempt_at,created_at)")

        db.execSQL("ALTER TABLE inventory_movements RENAME TO inventory_movements_v75")
        db.execSQL(
            """
            CREATE TABLE inventory_movements (
                id TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL, invoiceId TEXT NOT NULL,
                clientId TEXT NOT NULL, movementType TEXT NOT NULL, quantity INTEGER NOT NULL,
                quantityBefore INTEGER NOT NULL, quantityAfter INTEGER NOT NULL, unitPrice REAL NOT NULL,
                unit_price_minor INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL, shipmentId TEXT NOT NULL,
                source_type TEXT NOT NULL DEFAULT '', source_id TEXT NOT NULL DEFAULT '',
                source_version INTEGER NOT NULL DEFAULT 1, write_id TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL,
                organization_id TEXT, movement_kind TEXT, signed_base_quantity INTEGER, source_line_id TEXT,
                command_id TEXT, idempotency_key TEXT, posting_group_id TEXT, reverses_movement_id TEXT,
                conversion_factor_snapshot TEXT, occurred_at INTEGER, recorded_at INTEGER,
                server_accepted_at INTEGER, server_sequence INTEGER, created_by TEXT, device_id TEXT,
                contract_version INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO inventory_movements SELECT * FROM inventory_movements_v75")
        db.execSQL("DROP TABLE inventory_movements_v75")
        db.execSQL("CREATE INDEX index_inventory_movements_itemId ON inventory_movements(itemId)")
        db.execSQL("CREATE INDEX index_inventory_movements_invoiceId ON inventory_movements(invoiceId)")
        db.execSQL("CREATE INDEX index_inventory_movements_source_id ON inventory_movements(source_id)")
        db.execSQL("CREATE UNIQUE INDEX index_inventory_movements_org_idempotency ON inventory_movements(organization_id,idempotency_key)")
        db.execSQL("CREATE UNIQUE INDEX index_inventory_movements_org_reversal ON inventory_movements(organization_id,reverses_movement_id)")
        db.execSQL("CREATE INDEX index_inventory_movements_org_item_sequence ON inventory_movements(organization_id,itemId,server_sequence)")
        db.execSQL("CREATE INDEX index_inventory_movements_org_item_kind_occurred ON inventory_movements(organization_id,itemId,movement_kind,occurred_at)")
        db.execSQL("CREATE INDEX index_inventory_movements_org_source ON inventory_movements(organization_id,source_type,source_id,source_line_id)")
    }
}
