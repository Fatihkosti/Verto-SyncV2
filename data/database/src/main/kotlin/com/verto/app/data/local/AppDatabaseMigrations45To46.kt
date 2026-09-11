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

        db.execSQL("UPDATE clients SET nameSearch = ${normalizedTextSql("name")}")
        db.execSQL("UPDATE clients SET phoneSearch = ${normalizedPhoneSql("phone")}")
        db.execSQL("UPDATE inventory_items SET nameSearch = ${normalizedTextSql("name")}")
        db.execSQL("UPDATE inventory_items SET partNumberSearch = ${normalizedIdentifierSql("partNumber")}")
        db.execSQL("UPDATE inventory_items SET barcodeSearch = ${normalizedIdentifierSql("barcode")}")
        db.execSQL("UPDATE invoices SET invoiceNumberSearch = CAST(invoiceNumber AS TEXT)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_name_search ON clients(nameSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_phone_search ON clients(phoneSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_name_search ON inventory_items(nameSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_part_number_search ON inventory_items(partNumberSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_barcode_search ON inventory_items(barcodeSearch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_number_search ON invoices(invoiceNumberSearch)")
    }
}

private fun normalizedTextSql(column: String): String {
    var expression = "LOWER(TRIM(COALESCE($column, '')))"
    val replacements = listOf(
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
    replacements.forEach { (from, to) ->
        expression = "REPLACE($expression, '${from.sqlLiteral()}', '${to.sqlLiteral()}')"
    }
    repeat(5) { expression = "REPLACE($expression, '  ', ' ')" }
    return "TRIM($expression)"
}

private fun normalizedIdentifierSql(column: String): String {
    var expression = normalizedTextSql(column)
    listOf(" ", "-", "/", "\\", ".", "_", ":", "+", "(", ")").forEach { token ->
        expression = "REPLACE($expression, '${token.sqlLiteral()}', '')"
    }
    return expression
}

private fun normalizedPhoneSql(column: String): String {
    var expression = "COALESCE($column, '')"
    val digits = listOf(
        "\u0660" to "0", "\u0661" to "1", "\u0662" to "2", "\u0663" to "3", "\u0664" to "4",
        "\u0665" to "5", "\u0666" to "6", "\u0667" to "7", "\u0668" to "8", "\u0669" to "9",
        "\u06f0" to "0", "\u06f1" to "1", "\u06f2" to "2", "\u06f3" to "3", "\u06f4" to "4",
        "\u06f5" to "5", "\u06f6" to "6", "\u06f7" to "7", "\u06f8" to "8", "\u06f9" to "9"
    )
    digits.forEach { (from, to) -> expression = "REPLACE($expression, '$from', '$to')" }
    listOf(" ", "+", "-", "(", ")", ".", "/", "\\").forEach { token ->
        expression = "REPLACE($expression, '${token.sqlLiteral()}', '')"
    }
    return expression
}

private fun String.sqlLiteral(): String = replace("'", "''")
