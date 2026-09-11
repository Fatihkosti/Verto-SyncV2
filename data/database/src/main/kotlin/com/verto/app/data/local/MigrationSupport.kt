package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


// UUIDs للعملاء النظاميين — يجب أن تتطابق مع AppDatabase.CASH_CLIENT_UUID
internal const val CASH_CLIENT_UUID   = "00000000-0000-0000-0000-000000000001"
internal const val CASH_SUPPLIER_UUID = "00000000-0000-0000-0000-000000000002"

internal fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    val cursor = query("PRAGMA table_info($table)")
    cursor.use {
        while (it.moveToNext()) {
            if (it.getString(it.getColumnIndexOrThrow("name")) == column) return true
        }
    }
    return false
}
