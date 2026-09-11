package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v231 execution facts: customs lifecycle and centralized attachment actor metadata. */
val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN customs_started_at INTEGER")
        db.execSQL("ALTER TABLE logistics_milestones ADD COLUMN customs_completed_at INTEGER")
        db.execSQL("ALTER TABLE logistics_documents ADD COLUMN employee_id TEXT")
        db.execSQL("ALTER TABLE logistics_documents ADD COLUMN employee_name_snapshot TEXT")
    }
}
