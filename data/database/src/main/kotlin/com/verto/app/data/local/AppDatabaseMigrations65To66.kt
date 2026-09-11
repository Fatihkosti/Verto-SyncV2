package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F248 invoice lifecycle, optimistic versioning, immutable descriptive snapshots and hard-delete guard. */
val MIGRATION_65_66 = object : Migration(65, 66) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN lifecycle_status TEXT NOT NULL DEFAULT 'POSTED'")
        db.execSQL("ALTER TABLE invoices ADD COLUMN lifecycle_version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE invoices ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN void_reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN void_write_id TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE invoices SET lifecycle_status = CASE WHEN voided = 1 THEN 'VOID' ELSE 'POSTED' END, " +
                "posted_at = CASE WHEN voided = 0 THEN createdAt ELSE 0 END"
        )

        db.execSQL("ALTER TABLE invoice_items ADD COLUMN item_sku_snapshot TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN unit_snapshot TEXT NOT NULL DEFAULT ''")

        // Financial history cannot be hard-deleted. DRAFT is the only lifecycle eligible for DELETE.
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS prevent_posted_invoice_hard_delete
            BEFORE DELETE ON invoices
            WHEN OLD.lifecycle_status <> 'DRAFT'
            BEGIN
                SELECT RAISE(ABORT, 'posted invoices must be voided, not deleted');
            END
        """.trimIndent())
    }
}
