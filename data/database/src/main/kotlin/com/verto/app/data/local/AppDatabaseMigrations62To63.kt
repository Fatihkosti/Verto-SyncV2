package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F245 local atomicity/idempotency metadata. Additive only; no financial row is rewritten. */
val MIGRATION_62_63 = object : Migration(62, 63) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN organization_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN supplier_invoice_ref TEXT")
        db.execSQL("ALTER TABLE invoices ADD COLUMN supplier_invoice_ref_normalized TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_org_supplier_external_ref " +
                "ON invoices(organization_id, clientId, supplier_invoice_ref_normalized)"
        )

        db.execSQL("ALTER TABLE payments ADD COLUMN source_type TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN source_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE payments ADD COLUMN write_id TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN source_type TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN source_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN write_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_source_id ON inventory_movements(source_id)")

        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN source_type TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN source_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN write_id TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE audit_log ADD COLUMN sourceType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE audit_log ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE audit_log ADD COLUMN sourceVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE audit_log ADD COLUMN writeId TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_write_guard (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                operation_type TEXT NOT NULL,
                write_id TEXT NOT NULL,
                target_invoice_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                source_version INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_write_guard_identity " +
                "ON invoice_write_guard(organization_id, operation_type, write_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_invoice_write_guard_target " +
                "ON invoice_write_guard(target_invoice_id)"
        )
    }
}
