package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v257: additive schema for the canonical inventory ledger/cost contract.
 *
 * Existing movement rows deliberately remain contract_version=1 with null canonical identity.
 * Session 258 owns reconciliation/backfill; this migration must stay short and deterministic.
 */
val MIGRATION_72_73 = object : Migration(72, 73) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN organization_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN movement_kind TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN signed_base_quantity INTEGER")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN source_line_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN command_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN idempotency_key TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN posting_group_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN reverses_movement_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN conversion_factor_snapshot TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN occurred_at INTEGER")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN recorded_at INTEGER")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN server_accepted_at INTEGER")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN server_sequence INTEGER")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN created_by TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN device_id TEXT")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN contract_version INTEGER NOT NULL DEFAULT 1")

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_movements_org_idempotency " +
                "ON inventory_movements(organization_id, idempotency_key)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_movements_org_reversal " +
                "ON inventory_movements(organization_id, reverses_movement_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_movements_org_item_sequence " +
                "ON inventory_movements(organization_id, itemId, server_sequence)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_movements_org_item_kind_occurred " +
                "ON inventory_movements(organization_id, itemId, movement_kind, occurred_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_movements_org_source " +
                "ON inventory_movements(organization_id, source_type, source_id, source_line_id)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_cost_revisions (
                cost_revision_id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                source_line_id TEXT,
                revision_kind TEXT NOT NULL,
                direct_purchase_cost_minor INTEGER NOT NULL,
                landed_cost_per_base_unit_minor INTEGER NOT NULL,
                approved_inventory_cost_minor INTEGER NOT NULL,
                currency_code TEXT NOT NULL,
                exchange_rate_snapshot TEXT NOT NULL,
                allocation_basis TEXT NOT NULL,
                allocation_residual_minor INTEGER NOT NULL,
                is_provisional INTEGER NOT NULL,
                reverses_cost_revision_id TEXT,
                command_id TEXT NOT NULL,
                idempotency_key TEXT NOT NULL,
                cost_sequence INTEGER,
                approved_at INTEGER NOT NULL,
                recorded_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                device_id TEXT NOT NULL,
                contract_version INTEGER NOT NULL,
                FOREIGN KEY(item_id) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_cost_revisions_item_id ON inventory_cost_revisions(item_id)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_cost_revisions_org_idempotency " +
                "ON inventory_cost_revisions(organization_id, idempotency_key)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_cost_revisions_org_reversal " +
                "ON inventory_cost_revisions(organization_id, reverses_cost_revision_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_cost_revisions_org_item_sequence " +
                "ON inventory_cost_revisions(organization_id, item_id, cost_sequence)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_cost_revisions_org_source " +
                "ON inventory_cost_revisions(organization_id, source_type, source_id, source_line_id)"
        )
    }
}
