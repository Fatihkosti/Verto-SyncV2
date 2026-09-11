package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN inventoryItemId TEXT NOT NULL DEFAULT ''")

        db.execSQL("PRAGMA foreign_keys = OFF")
        db.execSQL("DELETE FROM inventory_movements WHERE itemId NOT IN (SELECT id FROM inventory_items)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_movements_new (
                id TEXT PRIMARY KEY NOT NULL,
                itemId TEXT NOT NULL,
                invoiceId TEXT NOT NULL DEFAULT '',
                clientId TEXT NOT NULL DEFAULT '',
                movementType TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                quantityBefore INTEGER NOT NULL,
                quantityAfter INTEGER NOT NULL,
                unitPrice REAL NOT NULL DEFAULT 0.0,
                note TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO inventory_movements_new
            SELECT id, itemId, invoiceId, clientId, movementType,
                   quantity, quantityBefore, quantityAfter,
                   unitPrice, note, createdAt
            FROM inventory_movements
        """.trimIndent())
        db.execSQL("DROP TABLE inventory_movements")
        db.execSQL("ALTER TABLE inventory_movements_new RENAME TO inventory_movements")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inv_movements_itemId ON inventory_movements(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inv_movements_invoiceId ON inventory_movements(invoiceId)")
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

// ✅ Migration 14 → 15
// استبدال IDs النصية للعملاء النظاميين بـ UUIDs ثابتة لتوافق Supabase
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")

        db.execSQL("UPDATE invoices SET clientId = '$CASH_CLIENT_UUID' WHERE clientId = 'cash_client_main'")
        db.execSQL("UPDATE payments SET clientId = '$CASH_CLIENT_UUID' WHERE clientId = 'cash_client_main'")
        db.execSQL("UPDATE clients  SET id       = '$CASH_CLIENT_UUID' WHERE id       = 'cash_client_main'")

        db.execSQL("UPDATE invoices SET clientId = '$CASH_SUPPLIER_UUID' WHERE clientId = 'cash_supplier_main'")
        db.execSQL("UPDATE payments SET clientId = '$CASH_SUPPLIER_UUID' WHERE clientId = 'cash_supplier_main'")
        db.execSQL("UPDATE clients  SET id       = '$CASH_SUPPLIER_UUID' WHERE id       = 'cash_supplier_main'")

        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

// Migration 15 → 16: نظام الوحدات + تصنيفات many-to-many + تنظيف inventory_items
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_units (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                quantityPerUnit REAL NOT NULL,
                unitType TEXT NOT NULL DEFAULT 'COUNT'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS item_categories (
                id TEXT PRIMARY KEY NOT NULL,
                itemId TEXT NOT NULL,
                category TEXT NOT NULL,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_categories_itemId ON item_categories(itemId)")

        val cursor = db.query("SELECT id, category FROM inventory_items WHERE category IS NOT NULL AND category != ''")
        while (cursor.moveToNext()) {
            val itemId   = cursor.getString(0)
            val category = cursor.getString(1)
            if (category.isNotBlank()) {
                val newId = java.util.UUID.randomUUID().toString()
                db.execSQL("INSERT INTO item_categories (id, itemId, category) VALUES (?, ?, ?)",
                    arrayOf(newId, itemId, category))
            }
        }
        cursor.close()

        db.execSQL("PRAGMA foreign_keys = OFF")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_items_new (
                id TEXT PRIMARY KEY NOT NULL,
                partNumber TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                unitId TEXT,
                linkedUnitItemId TEXT,
                isUnitItem INTEGER NOT NULL DEFAULT 0,
                isService INTEGER NOT NULL DEFAULT 0,
                buyPrice REAL NOT NULL DEFAULT 0.0,
                sellPrice REAL NOT NULL DEFAULT 0.0,
                quantity INTEGER NOT NULL DEFAULT 0,
                minQuantity INTEGER NOT NULL DEFAULT 5,
                location TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO inventory_items_new (
                id, partNumber, name, unitId, linkedUnitItemId, isUnitItem, isService,
                buyPrice, sellPrice, quantity, minQuantity, location, note, createdAt, updatedAt
            )
            SELECT id, partNumber, name, NULL, NULL, 0, 0,
                buyPrice, sellPrice, quantity, minQuantity, location, note, createdAt, updatedAt
            FROM inventory_items
        """.trimIndent())
        db.execSQL("DROP TABLE inventory_items")
        db.execSQL("ALTER TABLE inventory_items_new RENAME TO inventory_items")
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

// Migration 16 → 17: عمولة الفواتير + أرقام هواتف إضافية للعملاء
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN commission REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE clients ADD COLUMN secondaryPhones TEXT NOT NULL DEFAULT ''")
    }
}

// Migration 17 → 18: quantityPerUnit لكل بند وحدة
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN quantityPerUnit REAL NOT NULL DEFAULT 0.0")
    }
}

// Migration 18 → 19: سعر الشراء والكمية ورقم القطعة لكشف الأسعار
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE price_list_items ADD COLUMN buyPrice REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE price_list_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE price_list_items ADD COLUMN partNumber TEXT NOT NULL DEFAULT ''")
    }
}

// Migration 19 → 20: جدول التصنيفات المستقل
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS categories (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO categories (id, name)
            SELECT hex(randomblob(16)), category
            FROM (SELECT DISTINCT category FROM item_categories
                  WHERE category IS NOT NULL AND category != '')
        """.trimIndent())
    }
}

// Migration 20 → 21: ميزة الشحنات (4 جداول + حقول جديدة)
