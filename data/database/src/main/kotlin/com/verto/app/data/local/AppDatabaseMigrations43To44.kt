package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds recoverable leases, retry scheduling, and remote acknowledgement metadata to Optimal Outbox. */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN last_attempt_at INTEGER")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN next_attempt_at INTEGER")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN lease_owner TEXT")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN lease_token TEXT")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN lease_expires_at INTEGER")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN remote_id TEXT")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN remote_version INTEGER")
        db.execSQL("ALTER TABLE optimal_outbox ADD COLUMN synced_at INTEGER")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_outbox_org_status_next_attempt
            ON optimal_outbox(organization_id, status, next_attempt_at, created_at)
            """.trimIndent(),
        )
    }
}
