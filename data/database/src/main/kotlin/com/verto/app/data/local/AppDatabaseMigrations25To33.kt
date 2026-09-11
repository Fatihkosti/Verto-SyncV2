package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS join_codes (
                id TEXT PRIMARY KEY NOT NULL,
                clientId TEXT NOT NULL,
                clientName TEXT NOT NULL,
                code TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL,
                used INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_join_codes_clientId ON join_codes(clientId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_join_codes_expiresAt ON join_codes(expiresAt)")
    }
}

// SYNC-013: تحويل مفتاح price_list_items من Long autoGenerate إلى UUID نصّي
// لتمكين المزامنة متعددة الأجهزة (السيرفر يستخدم uuid). تُولَّد UUIDv4 للصفوف الموجودة.
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE price_list_items_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                buyPrice REAL NOT NULL,
                quantity INTEGER NOT NULL,
                partNumber TEXT NOT NULL,
                note TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO price_list_items_new (id, name, price, buyPrice, quantity, partNumber, note, sortOrder)
            SELECT
                lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
                substr(lower(hex(randomblob(2))), 2) || '-' ||
                substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                lower(hex(randomblob(6))),
                name, price, buyPrice, quantity, partNumber, note, sortOrder
            FROM price_list_items
        """.trimIndent())
        db.execSQL("DROP TABLE price_list_items")
        db.execSQL("ALTER TABLE price_list_items_new RENAME TO price_list_items")
    }
}

// SYNC-012: علم isDirty على clients (الشريحة الأولى المُختبَرة من الـ dirty flag).
// DEFAULT 1 ⇒ كل الصفوف الموجودة تُعتبر متسخة فتُرفع مرة بعد الترقية (أمان: لا فقدان رفع).
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clients ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
    }
}

// SYNC-012: تمديد الـ dirty flag إلى invoices.
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
    }
}

// SYNC-012: تمديد الـ dirty flag إلى payments + expenses.
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payments ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE expenses ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
    }
}

// SYNC-012: تمديد الـ dirty flag إلى inventory_items.
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
    }
}

// SYNC-012: تمديد الـ dirty flag إلى invoice_items.
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 1")
    }
}

// Notification infrastructure: local inbox with audience-based visibility.
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notifications (
                id TEXT PRIMARY KEY NOT NULL,
                organizationId TEXT NOT NULL,
                branchId TEXT,
                targetUserId TEXT,
                audience TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                relatedEntityId TEXT,
                relatedEntityType TEXT,
                navigationRoute TEXT,
                isRead INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                createdBy TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_organizationId ON notifications(organizationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_branchId ON notifications(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_targetUserId ON notifications(targetUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_audience ON notifications(audience)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_type ON notifications(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_createdAt ON notifications(createdAt)")
    }
}

// Employee invite codes metadata for future employee management screens.
