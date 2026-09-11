package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v372: preserve credit-installment editor drafts across process death. */
val MIGRATION_89_90 = object : Migration(89, 90) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `invoice_editor_drafts` ADD COLUMN `due_installments_json` TEXT NOT NULL DEFAULT '[]'")
    }
}
