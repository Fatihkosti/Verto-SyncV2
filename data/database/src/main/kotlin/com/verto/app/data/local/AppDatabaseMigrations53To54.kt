package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Logistics V2 operational-control foundation. Legacy rows are preserved without backfill. */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN cancelled_at INTEGER")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN cancel_reason TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN planned_departure_at INTEGER")
        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN handling_status TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN unloaded_at INTEGER")
        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN loaded_at INTEGER")

        db.execSQL("ALTER TABLE logistics_costs ADD COLUMN leg_id TEXT")
        db.execSQL("ALTER TABLE logistics_costs ADD COLUMN milestone_id TEXT")
        db.execSQL("ALTER TABLE logistics_costs ADD COLUMN source_id TEXT")

        db.execSQL("ALTER TABLE logistics_documents ADD COLUMN leg_id TEXT")
        db.execSQL("ALTER TABLE logistics_documents ADD COLUMN source_id TEXT")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipment_legs (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                from_milestone_id TEXT NOT NULL,
                to_milestone_id TEXT NOT NULL,
                mode TEXT NOT NULL,
                carrier_partner_id TEXT NOT NULL,
                status TEXT NOT NULL,
                planned_departure_at INTEGER,
                planned_arrival_at INTEGER,
                actual_departure_at INTEGER,
                actual_arrival_at INTEGER,
                road_vehicle_number TEXT,
                road_driver_name TEXT,
                road_driver_phone TEXT,
                sea_container_number TEXT,
                sea_bill_of_lading TEXT,
                sea_vessel_reference TEXT,
                air_waybill_number TEXT,
                air_flight_reference TEXT,
                note TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, from_milestone_id)
                    REFERENCES logistics_milestones(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, to_milestone_id)
                    REFERENCES logistics_milestones(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, carrier_partner_id)
                    REFERENCES logistics_partners(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_shipment_id_sequence " +
                "ON logistics_shipment_legs(organization_id, shipment_id, sequence)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_shipment_id " +
                "ON logistics_shipment_legs(organization_id, shipment_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_carrier_partner_id " +
                "ON logistics_shipment_legs(organization_id, carrier_partner_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_from_milestone_id " +
                "ON logistics_shipment_legs(organization_id, from_milestone_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_to_milestone_id " +
                "ON logistics_shipment_legs(organization_id, to_milestone_id)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_custody_handoffs (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                source_id TEXT,
                milestone_id TEXT,
                from_holder_type TEXT NOT NULL,
                from_holder_id TEXT,
                from_holder_name_snapshot TEXT NOT NULL,
                to_holder_type TEXT NOT NULL,
                to_holder_id TEXT,
                to_holder_name_snapshot TEXT NOT NULL,
                transferred_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                request_id TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, source_id)
                    REFERENCES logistics_shipment_sources(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, milestone_id)
                    REFERENCES logistics_milestones(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_custody_handoffs_organization_id_shipment_id_received_at " +
                "ON logistics_custody_handoffs(organization_id, shipment_id, received_at)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_logistics_custody_handoffs_organization_id_source_id_received_at " +
                "ON logistics_custody_handoffs(organization_id, source_id, received_at)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_custody_handoffs_organization_id_shipment_id_request_id " +
                "ON logistics_custody_handoffs(organization_id, shipment_id, request_id)",
        )
    }
}
