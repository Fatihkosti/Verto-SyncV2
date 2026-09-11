package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds a local-only maintenance lifecycle without extending any Optimal remote contract. */
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_maintenance_follow_ups (
                organization_id TEXT NOT NULL,
                record_id TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                expected_at INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, record_id),
                FOREIGN KEY(organization_id, record_id)
                    REFERENCES optimal_maintenance_records(organization_id, record_id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_maintenance_follow_ups_org_status_expected
            ON optimal_maintenance_follow_ups(organization_id, status, expected_at)
            """.trimIndent(),
        )
    }
}
