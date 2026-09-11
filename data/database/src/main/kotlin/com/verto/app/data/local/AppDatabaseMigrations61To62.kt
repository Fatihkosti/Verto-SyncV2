package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * F244 fixed-point money shadow columns.
 *
 * Legacy REAL columns remain temporarily for network/UI/report compatibility, but all new invoice
 * domain calculations use the INTEGER minor-unit columns as the persisted source of truth.
 * F246/F250 can remove compatibility reads only after their contracts are upgraded.
 */
val MIGRATION_61_62 = object : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addMinorColumns(db)
        backfillMinorValues(db)
        installRangeGuards(db)
    }

    private fun addMinorColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN total_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoices ADD COLUMN commission_minor INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE invoice_items ADD COLUMN buy_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN sell_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN total_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN adjusted_purchase_price_minor INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE payments ADD COLUMN amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cash_register ADD COLUMN balance_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN balance_before_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cash_register_movements ADD COLUMN balance_after_minor INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE inventory_items ADD COLUMN buy_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN sell_price_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN unit_price_minor INTEGER NOT NULL DEFAULT 0")
    }

    private fun backfillMinorValues(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE invoices SET total_amount_minor = CAST(ROUND(totalAmount * 100.0) AS INTEGER), commission_minor = CAST(ROUND(commission * 100.0) AS INTEGER)")
        db.execSQL("UPDATE invoice_items SET buy_price_minor = CAST(ROUND(buyPrice * 100.0) AS INTEGER), sell_price_minor = CAST(ROUND(sellPrice * 100.0) AS INTEGER), total_price_minor = CAST(ROUND(totalPrice * 100.0) AS INTEGER), adjusted_purchase_price_minor = CAST(ROUND(adjustedPurchasePrice * 100.0) AS INTEGER)")
        db.execSQL("UPDATE payments SET amount_minor = CAST(ROUND(amount * 100.0) AS INTEGER)")
        db.execSQL("UPDATE cash_register SET balance_minor = CAST(ROUND(balance * 100.0) AS INTEGER)")
        db.execSQL("UPDATE cash_register_movements SET amount_minor = CAST(ROUND(amount * 100.0) AS INTEGER), balance_before_minor = CAST(ROUND(balanceBefore * 100.0) AS INTEGER), balance_after_minor = CAST(ROUND(balanceAfter * 100.0) AS INTEGER)")
        db.execSQL("UPDATE inventory_items SET buy_price_minor = CAST(ROUND(buyPrice * 100.0) AS INTEGER), sell_price_minor = CAST(ROUND(sellPrice * 100.0) AS INTEGER)")
        db.execSQL("UPDATE inventory_movements SET unit_price_minor = CAST(ROUND(unitPrice * 100.0) AS INTEGER)")
    }

    private fun installRangeGuards(db: SupportSQLiteDatabase) {
        // 9e15 minor units keeps multiplication/summation safely below Long.MAX_VALUE.
        val max = 9_000_000_000_000_000L
        createGuard(db, "invoices", "total_amount_minor", max)
        createGuard(db, "invoices", "commission_minor", max)
        createGuard(db, "invoice_items", "buy_price_minor", max)
        createGuard(db, "invoice_items", "sell_price_minor", max)
        createGuard(db, "invoice_items", "total_price_minor", max)
        createGuard(db, "invoice_items", "adjusted_purchase_price_minor", max)
        createGuard(db, "payments", "amount_minor", max)
        createGuard(db, "cash_register", "balance_minor", max)
        createGuard(db, "cash_register_movements", "amount_minor", max)
        createGuard(db, "cash_register_movements", "balance_before_minor", max)
        createGuard(db, "cash_register_movements", "balance_after_minor", max)
        createGuard(db, "inventory_items", "buy_price_minor", max)
        createGuard(db, "inventory_items", "sell_price_minor", max)
        createGuard(db, "inventory_movements", "unit_price_minor", max)
    }

    private fun createGuard(db: SupportSQLiteDatabase, table: String, column: String, max: Long) {
        val safeTable = table.replace("`", "")
        val safeColumn = column.replace("`", "")
        val base = "guard_${safeTable}_${safeColumn}"
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${base}_insert BEFORE INSERT ON `$safeTable` " +
                "WHEN NEW.`$safeColumn` < -$max OR NEW.`$safeColumn` > $max " +
                "BEGIN SELECT RAISE(ABORT, 'money minor value out of range'); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${base}_update BEFORE UPDATE OF `$safeColumn` ON `$safeTable` " +
                "WHEN NEW.`$safeColumn` < -$max OR NEW.`$safeColumn` > $max " +
                "BEGIN SELECT RAISE(ABORT, 'money minor value out of range'); END"
        )
    }
}
