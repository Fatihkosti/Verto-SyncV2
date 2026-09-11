package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the tenant-scoped local cache for Optimal vehicle suggestions. */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_vehicles (
                organization_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                remote_vehicle_id TEXT NOT NULL,
                name TEXT NOT NULL,
                vehicle_type TEXT NOT NULL,
                plate_number TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, client_id, remote_vehicle_id),
                FOREIGN KEY(organization_id, client_id)
                    REFERENCES optimal_company_links(organization_id, client_id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_vehicles_org_client
            ON optimal_vehicles(organization_id, client_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_vehicles_org_client_name
            ON optimal_vehicles(organization_id, client_id, name)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_vehicles_org_client_type
            ON optimal_vehicles(organization_id, client_id, vehicle_type)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_vehicles_org_client_plate
            ON optimal_vehicles(organization_id, client_id, plate_number)
            """.trimIndent(),
        )
    }
}
