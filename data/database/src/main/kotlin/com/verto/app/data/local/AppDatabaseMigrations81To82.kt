package com.verto.app.data.local

import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verto.app.money.Money

/** Room field carrier keeps the legacy ExpenseEntity constructor/contract stable. */
open class ExpenseMinorAmount {
    @ColumnInfo(name = "amount_minor", defaultValue = "0")
    var amountMinor: Long = 0L
}

/** Session 334: add fixed-point expense authority while preserving row identity/lifecycle exactly. */
val MIGRATION_81_82 = object : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `amount_minor` INTEGER NOT NULL DEFAULT 0")
        db.query("SELECT `id`, `amount` FROM `expenses`").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val amountIndex = cursor.getColumnIndexOrThrow("amount")
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val amountMinor = Money.fromLegacyDouble(cursor.getDouble(amountIndex)).amountMinor
                db.execSQL(
                    "UPDATE `expenses` SET `amount_minor` = ? WHERE `id` = ?",
                    arrayOf(amountMinor, id),
                )
            }
        }
    }
}
