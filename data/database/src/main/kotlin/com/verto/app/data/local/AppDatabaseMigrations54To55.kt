package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Logistics Journey V3 foundation. Legacy Logistics V2 rows are preserved. */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateJourneyMilestones(db)
        migrateJourneyLegsAndCustody(db)
        migrateJourneyCostsAndDocuments(db)
        createLogisticsShortages(db)
        createLogisticsRecoveries(db)
        createLogisticsRecoveryLines(db)
        createLogisticsRecoveryPostings(db)
    }
}

private fun migrateJourneyMilestones(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN country_code TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN country_name_snapshot TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN city TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN place_name TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN plan_kind TEXT NOT NULL DEFAULT 'PLANNED'")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN expected_stay_days INTEGER")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN customs_broker_partner_id TEXT")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN customs_broker_name_snapshot TEXT")
    db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN customs_broker_phone_snapshot TEXT")
    db.execSQL("UPDATE logistics_milestones SET place_name = location WHERE place_name = ''")
}

private fun migrateJourneyLegsAndCustody(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN plan_kind TEXT NOT NULL DEFAULT 'PLANNED'")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN expected_transit_days INTEGER")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN representative_name_snapshot TEXT")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN representative_phone_snapshot TEXT")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN package_count INTEGER")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN weight_kg TEXT")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN superseded_at INTEGER")
    db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN superseded_by_leg_id TEXT")
    
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN handover_package_count INTEGER")
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN received_package_count INTEGER")
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN handover_weight_kg TEXT")
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN received_weight_kg TEXT")
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN package_change_reason TEXT")
    db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN package_change_note TEXT")
}

private fun migrateJourneyCostsAndDocuments(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN payment_state TEXT NOT NULL DEFAULT 'UNPAID'")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN cash_reference TEXT")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN cash_posted_base_amount TEXT")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN cash_posted_at INTEGER")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN reversal_of_cost_id TEXT")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN recovery_id TEXT")
    db.execSQL("ALTER TABLE logistics_costs ADD COLUMN request_id TEXT")
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_costs_organization_id_shipment_id_request_id " +
            "ON logistics_costs(organization_id, shipment_id, request_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_costs_organization_id_recovery_id " +
            "ON logistics_costs(organization_id, recovery_id)",
    )
    
    db.execSQL("ALTER TABLE logistics_documents ADD COLUMN handoff_id TEXT")
    db.execSQL("ALTER TABLE logistics_documents ADD COLUMN cost_id TEXT")
    db.execSQL("ALTER TABLE logistics_documents ADD COLUMN recovery_id TEXT")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_handoff_id " +
            "ON logistics_documents(organization_id, handoff_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_cost_id " +
            "ON logistics_documents(organization_id, cost_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_documents_organization_id_recovery_id " +
            "ON logistics_documents(organization_id, recovery_id)",
    )
}

private fun createLogisticsShortages(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS logistics_shortages (
            organization_id TEXT NOT NULL,
            id TEXT NOT NULL,
            shipment_id TEXT NOT NULL,
            shipment_line_id TEXT NOT NULL,
            original_missing_quantity INTEGER NOT NULL,
            remaining_missing_quantity INTEGER NOT NULL,
            base_purchase_unit_price_snapshot TEXT NOT NULL,
            status TEXT NOT NULL,
            detected_at INTEGER NOT NULL,
            note TEXT NOT NULL,
            request_id TEXT NOT NULL,
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
        "CREATE INDEX IF NOT EXISTS index_logistics_shortages_organization_id_shipment_id " +
            "ON logistics_shortages(organization_id, shipment_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_shortages_organization_id_status " +
            "ON logistics_shortages(organization_id, status)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shortages_organization_id_shipment_id_shipment_line_id " +
            "ON logistics_shortages(organization_id, shipment_id, shipment_line_id)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shortages_organization_id_shipment_id_request_id " +
            "ON logistics_shortages(organization_id, shipment_id, request_id)",
    )
}

private fun createLogisticsRecoveries(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS logistics_recoveries (
            organization_id TEXT NOT NULL,
            id TEXT NOT NULL,
            shipment_id TEXT NOT NULL,
            recovered_at INTEGER NOT NULL,
            employee_id TEXT NOT NULL,
            employee_name_snapshot TEXT NOT NULL,
            note TEXT NOT NULL,
            request_id TEXT NOT NULL,
            PRIMARY KEY(organization_id, id),
            FOREIGN KEY(organization_id, shipment_id)
                REFERENCES logistics_shipments(organization_id, id)
                ON UPDATE CASCADE ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recoveries_organization_id_shipment_id " +
            "ON logistics_recoveries(organization_id, shipment_id)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_recoveries_organization_id_shipment_id_request_id " +
            "ON logistics_recoveries(organization_id, shipment_id, request_id)",
    )
}

private fun createLogisticsRecoveryLines(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS logistics_recovery_lines (
            organization_id TEXT NOT NULL,
            id TEXT NOT NULL,
            recovery_id TEXT NOT NULL,
            shortage_id TEXT NOT NULL,
            shipment_line_id TEXT NOT NULL,
            recovered_quantity INTEGER NOT NULL,
            base_purchase_unit_price_snapshot TEXT NOT NULL,
            allocated_recovery_cost TEXT NOT NULL,
            PRIMARY KEY(organization_id, id),
            FOREIGN KEY(organization_id, recovery_id)
                REFERENCES logistics_recoveries(organization_id, id)
                ON UPDATE CASCADE ON DELETE CASCADE,
            FOREIGN KEY(organization_id, shortage_id)
                REFERENCES logistics_shortages(organization_id, id)
                ON UPDATE CASCADE ON DELETE RESTRICT,
            FOREIGN KEY(organization_id, shipment_line_id)
                REFERENCES logistics_shipment_lines(organization_id, id)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_lines_organization_id_recovery_id " +
            "ON logistics_recovery_lines(organization_id, recovery_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_lines_organization_id_shortage_id " +
            "ON logistics_recovery_lines(organization_id, shortage_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_lines_organization_id_shipment_line_id " +
            "ON logistics_recovery_lines(organization_id, shipment_line_id)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_recovery_lines_organization_id_recovery_id_shortage_id " +
            "ON logistics_recovery_lines(organization_id, recovery_id, shortage_id)",
    )
    
}

private fun createLogisticsRecoveryPostings(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS logistics_recovery_postings (
            organization_id TEXT NOT NULL,
            posting_id TEXT NOT NULL,
            recovery_id TEXT NOT NULL,
            recovery_line_id TEXT NOT NULL,
            shipment_id TEXT NOT NULL,
            shipment_line_id TEXT NOT NULL,
            quantity INTEGER NOT NULL,
            PRIMARY KEY(organization_id, posting_id),
            FOREIGN KEY(organization_id, recovery_id)
                REFERENCES logistics_recoveries(organization_id, id)
                ON UPDATE CASCADE ON DELETE CASCADE,
            FOREIGN KEY(organization_id, recovery_line_id)
                REFERENCES logistics_recovery_lines(organization_id, id)
                ON UPDATE CASCADE ON DELETE CASCADE,
            FOREIGN KEY(organization_id, shipment_line_id)
                REFERENCES logistics_shipment_lines(organization_id, id)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_postings_organization_id_shipment_id " +
            "ON logistics_recovery_postings(organization_id, shipment_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_postings_organization_id_recovery_id " +
            "ON logistics_recovery_postings(organization_id, recovery_id)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_recovery_postings_organization_id_recovery_line_id " +
            "ON logistics_recovery_postings(organization_id, recovery_line_id)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_logistics_recovery_postings_organization_id_shipment_line_id " +
            "ON logistics_recovery_postings(organization_id, shipment_line_id)",
    )
        
}
