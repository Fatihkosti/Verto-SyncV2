package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F247 latest-purchase-price policy, immutable sale cost snapshots and landed-cost audit events. */
val MIGRATION_64_65 = object : Migration(64, 65) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN unit_sell_price REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN unit_sell_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN unit_cost_at_sale REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN unit_cost_at_sale_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN line_revenue_snapshot REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN line_revenue_snapshot_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN line_cost_snapshot REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN line_cost_snapshot_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN gross_profit_snapshot REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN gross_profit_snapshot_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN cost_snapshot_status TEXT NOT NULL DEFAULT 'LEGACY_UNKNOWN'")
        // Revenue/sell price are historical facts we can safely backfill. Historical cost is not invented.
        db.execSQL("UPDATE invoice_items SET unit_sell_price = sellPrice, unit_sell_price_minor = sell_price_minor")
        db.execSQL("UPDATE invoice_items SET line_revenue_snapshot = totalPrice, line_revenue_snapshot_minor = total_price_minor")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_cost_revaluation_events (
                id TEXT NOT NULL PRIMARY KEY,
                item_id TEXT NOT NULL,
                quantity_before INTEGER NOT NULL,
                old_unit_cost_minor INTEGER NOT NULL,
                new_unit_cost_minor INTEGER NOT NULL,
                revaluation_difference_minor INTEGER NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                source_version INTEGER NOT NULL DEFAULT 1,
                actor_id TEXT NOT NULL DEFAULT '',
                actor_name TEXT NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(item_id) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_cost_revaluation_events_item_id ON inventory_cost_revaluation_events(item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_cost_revaluation_events_source_id ON inventory_cost_revaluation_events(source_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_cost_revaluation_identity ON inventory_cost_revaluation_events(source_type, source_id, write_id, item_id, new_unit_cost_minor)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS landed_cost_adjustment_events (
                id TEXT NOT NULL PRIMARY KEY,
                posting_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                quantity_at_adjustment INTEGER NOT NULL,
                previous_posting_unit_cost_minor INTEGER NOT NULL,
                new_posting_unit_cost_minor INTEGER NOT NULL,
                previous_inventory_unit_cost_minor INTEGER NOT NULL,
                new_inventory_unit_cost_minor INTEGER NOT NULL,
                revaluation_difference_minor INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(item_id) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_landed_cost_adjustment_events_item_id ON landed_cost_adjustment_events(item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_landed_cost_adjustment_events_shipment_id ON landed_cost_adjustment_events(shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_landed_cost_adjustment_identity ON landed_cost_adjustment_events(posting_id, new_posting_unit_cost_minor)")
    }
}
