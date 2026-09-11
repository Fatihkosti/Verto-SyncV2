package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_87_88 = object : Migration(87, 88) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `team_observations` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'IDEA'")
    }
}
