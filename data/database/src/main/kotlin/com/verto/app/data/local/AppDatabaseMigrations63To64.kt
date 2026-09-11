package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F246 currency truth, payment allocation and realized FX metadata. Additive and fail-closed for legacy international rows. */
val MIGRATION_63_64 = object : Migration(63, 64) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN transaction_currency_code TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN functional_currency_code TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN transaction_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN invoice_exchange_rate_snapshot TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN exchange_rate_direction TEXT NOT NULL DEFAULT 'FUNCTIONAL_PER_TRANSACTION'")
        db.execSQL("ALTER TABLE invoices ADD COLUMN exchange_rate_timestamp INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN exchange_rate_source TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoices ADD COLUMN functional_amount_at_recognition_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN legacy_currency_status TEXT NOT NULL DEFAULT 'REVIEW_REQUIRED'")
        db.execSQL("UPDATE invoices SET transaction_amount_minor = total_amount_minor")
        db.execSQL("UPDATE invoices SET legacy_currency_status = 'UNKNOWN' WHERE purchase_scope = 'INTERNATIONAL'")

        db.execSQL("ALTER TABLE payments ADD COLUMN payment_currency_code TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN supplier_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN payment_exchange_rate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN payment_exchange_rate_direction TEXT NOT NULL DEFAULT 'FUNCTIONAL_PER_TRANSACTION'")
        db.execSQL("ALTER TABLE payments ADD COLUMN payment_exchange_rate_timestamp INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN payment_exchange_rate_source TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN functional_cash_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN historical_functional_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN realized_fx_difference_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN legacy_currency_status TEXT NOT NULL DEFAULT 'REVIEW_REQUIRED'")
        db.execSQL("UPDATE payments SET supplier_amount_minor = amount_minor")
        db.execSQL("UPDATE payments SET legacy_currency_status = 'UNKNOWN' WHERE invoiceId IN (SELECT id FROM invoices WHERE legacy_currency_status = 'UNKNOWN')")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS payment_allocations (
                id TEXT NOT NULL PRIMARY KEY,
                payment_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                allocated_transaction_amount_minor INTEGER NOT NULL,
                historical_functional_amount_minor INTEGER NOT NULL,
                realized_fx_difference_minor INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                source_type TEXT NOT NULL DEFAULT 'INVOICE',
                source_id TEXT NOT NULL DEFAULT '',
                source_version INTEGER NOT NULL DEFAULT 1,
                write_id TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_allocations_payment_id ON payment_allocations(payment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_allocations_invoice_id ON payment_allocations(invoice_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_allocation_identity ON payment_allocations(payment_id, invoice_id)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS realized_fx_events (
                id TEXT NOT NULL PRIMARY KEY,
                payment_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                functional_currency_code TEXT NOT NULL,
                historical_functional_amount_minor INTEGER NOT NULL,
                functional_cash_amount_minor INTEGER NOT NULL,
                difference_minor INTEGER NOT NULL,
                result TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                source_type TEXT NOT NULL DEFAULT 'PAYMENT',
                source_id TEXT NOT NULL DEFAULT '',
                source_version INTEGER NOT NULL DEFAULT 1,
                write_id TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_realized_fx_events_payment_id ON realized_fx_events(payment_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_realized_fx_events_invoice_id ON realized_fx_events(invoice_id)")
    }
}
