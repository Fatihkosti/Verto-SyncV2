package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Logistics V2 batch receiving and idempotent stock-posting bookkeeping only. */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_receiving_batches (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                request_id TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                received_by_employee_id TEXT NOT NULL,
                received_by_employee_name_snapshot TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_receiving_batches_organization_id_shipment_id ON logistics_receiving_batches(organization_id, shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_receiving_batches_organization_id_request_id ON logistics_receiving_batches(organization_id, request_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_receiving_lines (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                batch_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                shipment_line_id TEXT NOT NULL,
                expected_quantity_snapshot INTEGER NOT NULL,
                received_quantity INTEGER NOT NULL,
                accepted_quantity INTEGER NOT NULL,
                damaged_quantity INTEGER NOT NULL,
                rejected_quantity INTEGER NOT NULL,
                quarantined_quantity INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, batch_id)
                    REFERENCES logistics_receiving_batches(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, shipment_line_id)
                    REFERENCES logistics_shipment_lines(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_receiving_lines_organization_id_batch_id ON logistics_receiving_lines(organization_id, batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_receiving_lines_organization_id_shipment_line_id ON logistics_receiving_lines(organization_id, shipment_line_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_receiving_lines_organization_id_batch_id_shipment_line_id ON logistics_receiving_lines(organization_id, batch_id, shipment_line_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_inventory_postings (
                organization_id TEXT NOT NULL,
                posting_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                receiving_batch_id TEXT NOT NULL,
                receiving_line_id TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                PRIMARY KEY(organization_id, posting_id),
                FOREIGN KEY(organization_id, receiving_batch_id)
                    REFERENCES logistics_receiving_batches(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, receiving_line_id)
                    REFERENCES logistics_receiving_lines(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_inventory_postings_organization_id_shipment_id ON logistics_inventory_postings(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_inventory_postings_organization_id_receiving_batch_id ON logistics_inventory_postings(organization_id, receiving_batch_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_inventory_postings_organization_id_receiving_line_id ON logistics_inventory_postings(organization_id, receiving_line_id)")
    }
}
