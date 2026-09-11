package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v234 introduces the explicit planning contract without rewriting legacy operational facts.
 * All additions are nullable/defaulted and legacy CUSTOMS milestones are intentionally preserved.
 */
val MIGRATION_60_61 = object : Migration(60, 61) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addShipmentPlanningColumns(db)
        addPurchasePlanningColumns(db)
        addLegPlanningColumns(db)
        createCustomsPlanTables(db)
        createPlanRevisionTables(db)

        db.execSQL(
            "UPDATE logistics_shipment_legs SET expected_transit_minutes = expected_transit_days * 1440 " +
                "WHERE expected_transit_minutes IS NULL AND expected_transit_days IS NOT NULL",
        )
    }

    private fun addShipmentPlanningColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN origin_country_key TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN origin_country_name_snapshot TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN origin_city TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN destination_country_key TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN destination_country_name_snapshot TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN destination_city TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN route_transport_plan_kind TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN unified_transport_mode TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN current_plan_revision INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN plan_approved_at INTEGER")
    }

    private fun addPurchasePlanningColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logistics_shipment_sources ADD COLUMN planned_package_count INTEGER")
        db.execSQL("ALTER TABLE logistics_shipment_sources ADD COLUMN planned_weight_kg TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_sources ADD COLUMN expected_ready_at INTEGER")
    }

    private fun addLegPlanningColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN expected_transit_minutes INTEGER")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_carrier_partner_id TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_carrier_name_snapshot TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_representative_name_snapshot TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_representative_phone_snapshot TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_package_count INTEGER")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_weight_kg TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_cost_amount TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_cost_currency TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_exchange_rate TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_base_cost_amount TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_proof_private_uri TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_proof_display_name TEXT")
        db.execSQL("ALTER TABLE logistics_shipment_legs ADD COLUMN planned_proof_mime_type TEXT")
    }

    private fun createCustomsPlanTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_customs_plans (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                checkpoint_name TEXT NOT NULL,
                after_station_id TEXT NOT NULL,
                expected_duration_minutes INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, after_station_id) REFERENCES logistics_milestones(organization_id, id) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_customs_plans_organization_id_shipment_id ON logistics_customs_plans(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_customs_plans_organization_id_after_station_id ON logistics_customs_plans(organization_id, after_station_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_customs_plan_documents (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                customs_plan_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                private_uri TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, customs_plan_id) REFERENCES logistics_customs_plans(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_customs_plan_documents_organization_id_shipment_id ON logistics_customs_plan_documents(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_customs_plan_documents_organization_id_customs_plan_id ON logistics_customs_plan_documents(organization_id, customs_plan_id)")
    }

    private fun createPlanRevisionTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_plan_revisions (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                revision_number INTEGER NOT NULL,
                kind TEXT NOT NULL,
                reason TEXT NOT NULL,
                changed_by_employee_id TEXT,
                changed_by_employee_name_snapshot TEXT,
                recorded_at INTEGER NOT NULL,
                request_id TEXT NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_plan_revisions_organization_id_shipment_id_revision_number ON logistics_plan_revisions(organization_id, shipment_id, revision_number)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_plan_revisions_organization_id_request_id ON logistics_plan_revisions(organization_id, request_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_plan_revision_changes (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                revision_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                scope TEXT NOT NULL,
                scope_id TEXT,
                field_key TEXT NOT NULL,
                previous_value TEXT,
                new_value TEXT,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, revision_id) REFERENCES logistics_plan_revisions(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_plan_revision_changes_organization_id_revision_id ON logistics_plan_revision_changes(organization_id, revision_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_plan_revision_changes_organization_id_shipment_id ON logistics_plan_revision_changes(organization_id, shipment_id)")
    }
}
