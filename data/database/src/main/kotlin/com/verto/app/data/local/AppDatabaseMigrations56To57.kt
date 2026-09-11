package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v230 planning: route legs may exist before a carrier is selected; carrier becomes execution data in v231. */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logistics_shipment_legs_v230 (
                organization_id TEXT NOT NULL,
                id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                from_milestone_id TEXT NOT NULL,
                to_milestone_id TEXT NOT NULL,
                mode TEXT NOT NULL,
                carrier_partner_id TEXT,
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
                plan_kind TEXT NOT NULL DEFAULT 'PLANNED',
                expected_transit_days INTEGER,
                representative_name_snapshot TEXT,
                representative_phone_snapshot TEXT,
                package_count INTEGER,
                weight_kg TEXT,
                superseded_at INTEGER,
                superseded_by_leg_id TEXT,
                PRIMARY KEY(organization_id, id),
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(organization_id, from_milestone_id) REFERENCES logistics_milestones(organization_id, id) ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, to_milestone_id) REFERENCES logistics_milestones(organization_id, id) ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, carrier_partner_id) REFERENCES logistics_partners(organization_id, id) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO logistics_shipment_legs_v230(
                organization_id,id,shipment_id,sequence,from_milestone_id,to_milestone_id,mode,carrier_partner_id,status,
                planned_departure_at,planned_arrival_at,actual_departure_at,actual_arrival_at,
                road_vehicle_number,road_driver_name,road_driver_phone,sea_container_number,sea_bill_of_lading,sea_vessel_reference,
                air_waybill_number,air_flight_reference,note,plan_kind,expected_transit_days,representative_name_snapshot,
                representative_phone_snapshot,package_count,weight_kg,superseded_at,superseded_by_leg_id
            )
            SELECT organization_id,id,shipment_id,sequence,from_milestone_id,to_milestone_id,mode,carrier_partner_id,status,
                planned_departure_at,planned_arrival_at,actual_departure_at,actual_arrival_at,
                road_vehicle_number,road_driver_name,road_driver_phone,sea_container_number,sea_bill_of_lading,sea_vessel_reference,
                air_waybill_number,air_flight_reference,note,plan_kind,expected_transit_days,representative_name_snapshot,
                representative_phone_snapshot,package_count,weight_kg,superseded_at,superseded_by_leg_id
            FROM logistics_shipment_legs
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE logistics_shipment_legs")
        db.execSQL("ALTER TABLE logistics_shipment_legs_v230 RENAME TO logistics_shipment_legs")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_shipment_id_sequence ON logistics_shipment_legs(organization_id, shipment_id, sequence)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_shipment_id ON logistics_shipment_legs(organization_id, shipment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_carrier_partner_id ON logistics_shipment_legs(organization_id, carrier_partner_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_from_milestone_id ON logistics_shipment_legs(organization_id, from_milestone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_logistics_shipment_legs_organization_id_to_milestone_id ON logistics_shipment_legs(organization_id, to_milestone_id)")
    }
}
