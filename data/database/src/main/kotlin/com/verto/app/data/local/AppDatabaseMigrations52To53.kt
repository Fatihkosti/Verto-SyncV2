package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Logistics V2 landed-cost allocation persistence. Legacy tables are untouched. */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_cost_allocations (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                shipment_line_id TEXT NOT NULL,
                amount TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, shipment_line_id)
                    REFERENCES logistics_shipment_lines(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_cost_allocations_organization_id_shipment_id " +
                "ON logistics_cost_allocations(organization_id, shipment_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_cost_allocations_organization_id_shipment_line_id " +
                "ON logistics_cost_allocations(organization_id, shipment_line_id)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_cost_allocations_organization_id_shipment_id_shipment_line_id " +
                "ON logistics_cost_allocations(organization_id, shipment_id, shipment_line_id)",
        )
    }
}
