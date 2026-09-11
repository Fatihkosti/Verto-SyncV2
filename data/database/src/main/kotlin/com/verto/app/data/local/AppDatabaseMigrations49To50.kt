package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Logistics V2 core persistence without changing or backfilling legacy shipment tables. */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipments (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_number TEXT NOT NULL,
                source_location TEXT NOT NULL,
                destination_location TEXT NOT NULL,
                state TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                transport_mode TEXT,
                assigned_employee_id TEXT,
                assigned_employee_name_snapshot TEXT,
                started_at INTEGER,
                expected_departure_at INTEGER,
                expected_arrival_at INTEGER,
                notes TEXT NOT NULL,
                PRIMARY KEY(organization_id, id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipments_organization_id_shipment_number ON logistics_shipments(organization_id, shipment_number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipments_organization_id_state ON logistics_shipments(organization_id, state)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipment_sources (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                supplier_id TEXT NOT NULL,
                supplier_name_snapshot TEXT NOT NULL,
                invoice_number_snapshot TEXT NOT NULL,
                original_currency TEXT,
                exchange_rate_snapshot TEXT,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_sources_organization_id_shipment_id ON logistics_shipment_sources(organization_id, shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipment_sources_organization_id_shipment_id_invoice_id ON logistics_shipment_sources(organization_id, shipment_id, invoice_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipment_lines (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                source_invoice_id TEXT NOT NULL,
                source_invoice_item_id TEXT NOT NULL,
                inventory_item_id TEXT NOT NULL,
                item_name_snapshot TEXT NOT NULL,
                expected_quantity INTEGER NOT NULL,
                base_purchase_unit_price TEXT NOT NULL,
                hs_code TEXT,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_lines_organization_id_shipment_id ON logistics_shipment_lines(organization_id, shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipment_lines_organization_id_shipment_id_source_invoice_item_id ON logistics_shipment_lines(organization_id, shipment_id, source_invoice_item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_lines_organization_id_inventory_item_id ON logistics_shipment_lines(organization_id, inventory_item_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_milestones (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                type TEXT NOT NULL,
                milestone_order INTEGER NOT NULL,
                location TEXT NOT NULL,
                planned_arrival_at INTEGER,
                arrived_at INTEGER,
                departed_at INTEGER,
                note TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_milestones_organization_id_shipment_id ON logistics_milestones(organization_id, shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_milestones_organization_id_shipment_id_milestone_order ON logistics_milestones(organization_id, shipment_id, milestone_order)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_assignments (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                employee_id TEXT NOT NULL,
                employee_name_snapshot TEXT NOT NULL,
                assigned_at INTEGER NOT NULL,
                ended_at INTEGER,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_assignments_organization_id_shipment_id ON logistics_assignments(organization_id, shipment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_assignments_organization_id_shipment_id_assigned_at ON logistics_assignments(organization_id, shipment_id, assigned_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_assignments_organization_id_employee_id ON logistics_assignments(organization_id, employee_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_events (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                type TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                employee_id TEXT,
                employee_name_snapshot TEXT,
                request_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_events_organization_id_shipment_id_occurred_at ON logistics_events(organization_id, shipment_id, occurred_at)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_events_organization_id_shipment_id_request_id_type ON logistics_events(organization_id, shipment_id, request_id, type)")
    }
}
