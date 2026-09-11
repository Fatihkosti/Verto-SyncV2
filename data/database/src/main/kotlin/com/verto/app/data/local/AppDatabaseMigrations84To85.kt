package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 351: invoice-level discount plus explicit commission/referral attribution. */
val MIGRATION_84_85 = object : Migration(84, 85) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `invoices` ADD COLUMN `discount` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `invoices` ADD COLUMN `discount_minor` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `invoices` ADD COLUMN `commission_beneficiary_client_id` TEXT")
        db.execSQL("ALTER TABLE `invoices` ADD COLUMN `commission_source` TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_commission_beneficiary` ON `invoices` (`commission_beneficiary_client_id`)")
        db.execSQL("UPDATE `invoices` SET `commission_beneficiary_client_id` = `clientId`, `commission_source` = 'BUYER' WHERE `category` = 'SALE' AND `commission` > 0 AND `commission_beneficiary_client_id` IS NULL")
        db.execSQL("ALTER TABLE `invoice_editor_drafts` ADD COLUMN `discount` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `invoice_editor_drafts` ADD COLUMN `referrer_client_id` TEXT NOT NULL DEFAULT ''")
    }
}
