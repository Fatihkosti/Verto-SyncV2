package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v229 logistics foundation: explicit purchase scope, atomic numbering, event time, payment separation and planning metadata. */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN purchase_scope TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL("UPDATE invoices SET purchase_scope = 'INTERNATIONAL' WHERE category = 'PURCHASE' AND (shipmentId IS NOT NULL OR clientId IN (SELECT id FROM clients WHERE clientType LIKE '%GLOBAL_SUPPLIER%'))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_purchase_scope ON invoices(purchase_scope)")

        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN customs_milestone_id TEXT")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN customs_calendar_policy_id TEXT NOT NULL DEFAULT 'FRIDAY_OFF'")
        db.execSQL("ALTER TABLE logistics_shipments ADD COLUMN event_timezone_id TEXT NOT NULL DEFAULT 'UTC'")
        db.execSQL("UPDATE logistics_shipments SET state = 'AT_STATION' WHERE state = 'ARRIVED'")
        db.execSQL("UPDATE logistics_shipments SET state = 'RECEIVING' WHERE state IN ('PARTIAL', 'RECEIVED')")

        db.execSQL("ALTER TABLE logistics_events ADD COLUMN recorded_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE logistics_events SET recorded_at = occurred_at WHERE recorded_at = 0")

        db.execSQL("ALTER TABLE logistics_partners ADD COLUMN representative_name TEXT")
        db.execSQL("ALTER TABLE logistics_partners ADD COLUMN representative_phone TEXT")
        db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN opened_package_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE logistics_custody_handoffs ADD COLUMN damaged_package_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE logistics_costs ADD COLUMN exchange_rate_date INTEGER")

        db.execSQL("CREATE TABLE IF NOT EXISTS logistics_shipment_number_sequences (organization_id TEXT NOT NULL PRIMARY KEY, last_number INTEGER NOT NULL)")
        db.execSQL("INSERT OR REPLACE INTO logistics_shipment_number_sequences(organization_id, last_number) SELECT organization_id, COALESCE(MAX(CAST(shipment_number AS INTEGER)), 0) FROM logistics_shipments WHERE shipment_number != '' AND shipment_number NOT GLOB '*[^0-9]*' GROUP BY organization_id")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS logistics_payments (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                cost_id TEXT NOT NULL,
                state TEXT NOT NULL,
                amount TEXT NOT NULL,
                currency TEXT NOT NULL DEFAULT 'SDG',
                account_id TEXT,
                paid_at INTEGER,
                reference TEXT,
                proof_document_id TEXT,
                cash_reference TEXT,
                request_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, cost_id) REFERENCES logistics_costs(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_payments_organization_id_shipment_id ON logistics_payments(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_payments_organization_id_cost_id ON logistics_payments(organization_id, cost_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_payments_organization_id_request_id ON logistics_payments(organization_id, request_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_payments_organization_id_cash_reference ON logistics_payments(organization_id, cash_reference)")

        // Preserve historical paid facts while moving new payment truth to its own table.
        db.execSQL("""
            INSERT INTO logistics_payments(
                organization_id, id, shipment_id, cost_id, state, amount, currency,
                paid_at, reference, cash_reference, request_id, created_at
            )
            SELECT organization_id,
                   id || ':legacy-payment',
                   shipment_id,
                   id,
                   CASE WHEN payment_state = 'REVERSED' THEN 'REVERSED' ELSE 'PAID' END,
                   COALESCE(cash_posted_base_amount, base_currency_amount),
                   'SDG',
                   cash_posted_at,
                   reference,
                   cash_reference,
                   id || ':legacy-payment',
                   COALESCE(cash_posted_at, 0)
            FROM logistics_costs
            WHERE payment_state IN ('PAID', 'REVERSED') AND cash_reference IS NOT NULL
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS logistics_route_templates (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                origin_country_code TEXT NOT NULL,
                origin_city TEXT NOT NULL,
                destination_country_code TEXT NOT NULL,
                destination_city TEXT NOT NULL,
                transport_plan_kind TEXT NOT NULL,
                unified_transport_mode TEXT,
                customs_stop_order INTEGER,
                expected_customs_minutes INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, id)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_route_templates_organization_id_name ON logistics_route_templates(organization_id, name)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS logistics_route_template_stops (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                template_id TEXT NOT NULL,
                stop_order INTEGER NOT NULL,
                country_code TEXT NOT NULL,
                city TEXT NOT NULL,
                place_name TEXT NOT NULL DEFAULT '',
                expected_transit_minutes_to_next INTEGER,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, template_id) REFERENCES logistics_route_templates(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_route_template_stops_organization_id_template_id ON logistics_route_template_stops(organization_id, template_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_route_template_stops_organization_id_template_id_stop_order ON logistics_route_template_stops(organization_id, template_id, stop_order)")
    }
}
