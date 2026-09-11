package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v370: contractual installment schedule for credit invoices. */
val MIGRATION_88_89 = object : Migration(88, 89) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `invoice_due_installments` (
                `id` TEXT NOT NULL,
                `invoice_id` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `amount_minor` INTEGER NOT NULL,
                `currency_code` TEXT NOT NULL,
                `due_date` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `write_id` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`invoice_id`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_due_installments_invoice` ON `invoice_due_installments` (`invoice_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoice_due_installments_sequence` ON `invoice_due_installments` (`invoice_id`, `sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_due_installments_due_date` ON `invoice_due_installments` (`due_date`)")
    }
}
