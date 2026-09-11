package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Logistics V2 journey, partners, transport details, documents, and raw costs only. */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_partners (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                role TEXT NOT NULL,
                phone TEXT,
                notes TEXT NOT NULL,
                PRIMARY KEY(organization_id, id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_partners_organization_id_name_role ON logistics_partners(organization_id, name, role)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipment_partner_links (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                partner_id TEXT NOT NULL,
                role TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, partner_id)
                    REFERENCES logistics_partners(organization_id, id)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_partner_links_organization_id_shipment_id ON logistics_shipment_partner_links(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_partner_links_organization_id_partner_id ON logistics_shipment_partner_links(organization_id, partner_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipment_partner_links_organization_id_shipment_id_partner_id_role ON logistics_shipment_partner_links(organization_id, shipment_id, partner_id, role)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_transport_details (
                organization_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                incoterm_code TEXT,
                container_number TEXT,
                bill_or_airway_number TEXT,
                vessel_or_flight_reference TEXT,
                weight_kg TEXT,
                volume_m3 TEXT,
                package_count INTEGER,
                pallet_count INTEGER,
                insurance_reference TEXT,
                PRIMARY KEY(organization_id, shipment_id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_transport_details_organization_id_shipment_id ON logistics_transport_details(organization_id, shipment_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_documents (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                milestone_id TEXT,
                type TEXT NOT NULL,
                display_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                private_uri TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, milestone_id)
                    REFERENCES logistics_milestones(organization_id, id)
                    ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_shipment_id ON logistics_documents(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_milestone_id ON logistics_documents(organization_id, milestone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_shipment_id_sha256 ON logistics_documents(organization_id, shipment_id, sha256)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_costs (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                type TEXT NOT NULL,
                amount TEXT NOT NULL,
                currency TEXT NOT NULL,
                exchange_rate_snapshot TEXT NOT NULL,
                base_currency_amount TEXT NOT NULL,
                status TEXT NOT NULL,
                service_partner_id TEXT,
                reference TEXT,
                note TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id)
                    REFERENCES logistics_shipments(organization_id, id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, service_partner_id)
                    REFERENCES logistics_partners(organization_id, id)
                    ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_costs_organization_id_shipment_id ON logistics_costs(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_costs_organization_id_service_partner_id ON logistics_costs(organization_id, service_partner_id)")
    }
}
