package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** B05: bind every unified-outbox lease to the session scope that acquired it. */
val MIGRATION_97_98 = object : Migration(97, 98) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_outbox ADD COLUMN lease_scope_epoch INTEGER")
    }
}
