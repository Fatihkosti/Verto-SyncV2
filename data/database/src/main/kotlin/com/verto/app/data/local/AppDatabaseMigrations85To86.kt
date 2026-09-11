package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 352: organization settings use an explicit local dirty marker for safe retry/convergence. */
val MIGRATION_85_86 = object : Migration(85, 86) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `organization_settings_local` ADD COLUMN `is_dirty` INTEGER NOT NULL DEFAULT 0")
    }
}
