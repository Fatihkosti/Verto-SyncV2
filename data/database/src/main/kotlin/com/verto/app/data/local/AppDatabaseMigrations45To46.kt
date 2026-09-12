package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds normalized prefix-search keys and a local-only optional inventory barcode. */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clients ADD COLUMN nameSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE clients ADD COLUMN phoneSearch TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE inventory_items ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN nameSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN partNumberSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN barcodeSearch TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE invoices ADD COLUMN invoiceNumberSearch TEXT NOT NULL DEFAULT ''")

        backfillNormalizedText(db, "clients", "nameSearch", "name")
        backfillNormalizedPhone(db, "clients", "phoneSearch", "phone")
        backfillNormalizedText(db, "inventory_items", "nameSearch", "name")
        backfillNormalizedIdentifier(db, "inventory_items", "partNumberSearch", "partNumber")
        backfillNormalizedIdentifier(db, "inventory_items", "barcodeSearch", "barcode")
        db.execSQL("UPDATE invoices SET invoiceNumberSearch = CAST(invoiceNumber AS TEXT)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_name_search ON clients(nameSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_phone_search ON clients(phoneSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_name_search ON inventory_items(nameSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_part_number_search ON inventory_items(partNumberSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_barcode_search ON inventory_items(barcodeSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_number_search ON invoices(invoiceNumberSearch)")
    }
}

private val textNormalizationReplacements = listOf(
        "\u0623" to "\u0627", "\u0625" to "\u0627", "\u0622" to "\u0627", "\u0671" to "\u0627",
        "\u0624" to "\u0648", "\u0626" to "\u064a", "\u0649" to "\u064a", "\u0629" to "\u0647",
        "\u0660" to "0", "\u0661" to "1", "\u0662" to "2", "\u0663" to "3", "\u0664" to "4",
        "\u0665" to "5", "\u0666" to "6", "\u0667" to "7", "\u0668" to "8", "\u0669" to "9",
        "\u06f0" to "0", "\u06f1" to "1", "\u06f2" to "2", "\u06f3" to "3", "\u06f4" to "4",
        "\u06f5" to "5", "\u06f6" to "6", "\u06f7" to "7", "\u06f8" to "8", "\u06f9" to "9",
        "\u0640" to "",
        "\u064e" to "", "\u064b" to "", "\u064f" to "", "\u064c" to "", "\u0650" to "", "\u064d" to "",
        "\u0652" to "", "\u0651" to "", "\u0670" to "",
        "-" to " ", "/" to " ", "\\" to " ", "." to " ", "," to " ",
        "\u060c" to " ", "(" to " ", ")" to " ", "[" to " ", "]" to " ",
        "_" to " ", "+" to " ", ":" to " ", ";" to " "
    )

private fun backfillNormalizedText(
    db: SupportSQLiteDatabase,
    table: String,
    targetColumn: String,
    sourceColumn: String,
) {
    db.execSQL("UPDATE $table SET $targetColumn = LOWER(TRIM(COALESCE($sourceColumn, '')))")
    textNormalizationReplacements.forEach { (from, to) ->
        db.execSQL(
            "UPDATE $table SET $targetColumn = REPLACE($targetColumn, '${from.sqlLiteral()}', '${to.sqlLiteral()}')",
        )
    }
    repeat(5) { db.execSQL("UPDATE $table SET $targetColumn = REPLACE($targetColumn, '  ', ' ')") }
    db.execSQL("UPDATE $table SET $targetColumn = TRIM($targetColumn)")
}

private fun backfillNormalizedIdentifier(
    db: SupportSQLiteDatabase,
    table: String,
    targetColumn: String,
    sourceColumn: String,
) {
    backfillNormalizedText(db, table, targetColumn, sourceColumn)
    listOf(" ", "-", "/", "\\", ".", "_", ":", "+", "(", ")").forEach { token ->
        db.execSQL(
            "UPDATE $table SET $targetColumn = REPLACE($targetColumn, '${token.sqlLiteral()}', '')",
        )
    }
}

private fun backfillNormalizedPhone(
    db: SupportSQLiteDatabase,
    table: String,
    targetColumn: String,
    sourceColumn: String,
) {
    db.execSQL("UPDATE $table SET $targetColumn = COALESCE($sourceColumn, '')")
    val digits = listOf(
        "\u0660" to "0", "\u0661" to "1", "\u0662" to "2", "\u0663" to "3", "\u0664" to "4",
        "\u0665" to "5", "\u0666" to "6", "\u0667" to "7", "\u0668" to "8", "\u0669" to "9",
        "\u06f0" to "0", "\u06f1" to "1", "\u06f2" to "2", "\u06f3" to "3", "\u06f4" to "4",
        "\u06f5" to "5", "\u06f6" to "6", "\u06f7" to "7", "\u06f8" to "8", "\u06f9" to "9"
    )
    digits.forEach { (from, to) ->
        db.execSQL("UPDATE $table SET $targetColumn = REPLACE($targetColumn, '$from', '$to')")
    }
    listOf(" ", "+", "-", "(", ")", ".", "/", "\\").forEach { token ->
        db.execSQL(
            "UPDATE $table SET $targetColumn = REPLACE($targetColumn, '${token.sqlLiteral()}', '')",
        )
    }
}

private fun String.sqlLiteral(): String = replace("'", "''")
