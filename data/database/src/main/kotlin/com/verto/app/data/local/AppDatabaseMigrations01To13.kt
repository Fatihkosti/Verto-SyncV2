package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS invoice_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                buyPrice REAL NOT NULL DEFAULT 0.0,
                sellPrice REAL NOT NULL DEFAULT 0.0,
                totalPrice REAL NOT NULL DEFAULT 0.0,
                FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_items_invoiceId ON invoice_items(invoiceId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clients ADD COLUMN clientType TEXT NOT NULL DEFAULT 'INDIVIDUAL'")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN itemType TEXT NOT NULL DEFAULT 'GOODS'")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN isOwedToMe INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE invoices ADD COLUMN imageUri TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS client_reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                clientId INTEGER NOT NULL,
                note TEXT NOT NULL,
                reminderAt INTEGER NOT NULL,
                isDone INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(clientId) REFERENCES clients(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_client_reminders_clientId ON client_reminders(clientId)")
    }
}

// تراجع عن تغيير مخطط له ولم يُنفَّذ — no-op إلزامي للحفاظ على سلسلة الـ migrations
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS price_list_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS price_list_header (
                id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                shopName TEXT NOT NULL DEFAULT '',
                address TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_items (
                id TEXT PRIMARY KEY NOT NULL,
                partNumber TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                brand TEXT NOT NULL DEFAULT '',
                carModel TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
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
            CREATE TABLE IF NOT EXISTS inventory_movements (
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
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inv_movements_itemId ON inventory_movements(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inv_movements_invoiceId ON inventory_movements(invoiceId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN status TEXT NOT NULL DEFAULT 'CLOSED_CASH'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS invoices_new (
                id TEXT PRIMARY KEY NOT NULL,
                invoiceNumber INTEGER NOT NULL,
                clientId TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL,
                totalAmount REAL NOT NULL,
                createdAt INTEGER NOT NULL,
                dueDate INTEGER NOT NULL,
                notifyDaysBefore TEXT NOT NULL DEFAULT '1,3,7',
                notifyRepeatDays INTEGER NOT NULL DEFAULT 3,
                notificationsEnabled INTEGER NOT NULL DEFAULT 1,
                notes TEXT NOT NULL DEFAULT '',
                isOwedToMe INTEGER NOT NULL DEFAULT 1,
                imageUri TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'CLOSED_CASH',
                FOREIGN KEY(clientId) REFERENCES clients(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO invoices_new (
                id, invoiceNumber, clientId, type, description, totalAmount,
                createdAt, dueDate, notifyDaysBefore, notifyRepeatDays,
                notificationsEnabled, notes, isOwedToMe, imageUri, status
            )
            SELECT
                id, invoiceNumber, clientId, type, description, totalAmount,
                createdAt, dueDate, notifyDaysBefore, notifyRepeatDays,
                notificationsEnabled, notes, isOwedToMe, imageUri, status
            FROM invoices
        """.trimIndent())
        db.execSQL("DROP TABLE invoices")
        db.execSQL("ALTER TABLE invoices_new RENAME TO invoices")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_clientId ON invoices(clientId)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE invoices SET status = 'CLOSED_CASH' WHERE status = 'OPEN'")
        db.execSQL("UPDATE invoices SET type = 'GOODS' WHERE type = 'CASH' OR type = 'SERVICE'")
        db.execSQL("UPDATE invoice_items SET itemType = 'GOODS' WHERE itemType = 'SERVICE' OR itemType = 'CASH'")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN category TEXT NOT NULL DEFAULT 'SALE'")
        db.execSQL("UPDATE invoices SET category = 'PURCHASE' WHERE isOwedToMe = 0")
        db.execSQL("UPDATE invoices SET category = 'SALE'     WHERE isOwedToMe = 1")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN itemCategory TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN employeeId   TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN employeeName TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_register (
                id TEXT PRIMARY KEY NOT NULL DEFAULT 'main',
                balance REAL NOT NULL DEFAULT 0.0,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT OR IGNORE INTO cash_register (id, balance, updatedAt) VALUES ('main', 0.0, ${System.currentTimeMillis()})")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_register_movements (
                id TEXT PRIMARY KEY NOT NULL,
                movementType TEXT NOT NULL,
                amount REAL NOT NULL,
                balanceBefore REAL NOT NULL,
                balanceAfter REAL NOT NULL,
                referenceId TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_log (
                id TEXT PRIMARY KEY NOT NULL,
                action TEXT NOT NULL,
                auditTable TEXT NOT NULL,
                recordId TEXT NOT NULL,
                recordSummary TEXT NOT NULL DEFAULT '',
                oldValue TEXT NOT NULL DEFAULT '',
                newValue TEXT NOT NULL DEFAULT '',
                employeeId TEXT NOT NULL DEFAULT '',
                employeeName TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                canUndo INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_recordId ON audit_log(recordId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_createdAt ON audit_log(createdAt)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clients ADD COLUMN carType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE clients ADD COLUMN bankAccount TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE clients ADD COLUMN specialty TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            UPDATE invoices
            SET invoiceNumber = invoiceNumber + (
                SELECT COUNT(*) FROM invoices AS i2
                WHERE i2.invoiceNumber = invoices.invoiceNumber
                  AND i2.rowid < invoices.rowid
            ) * 100000
            WHERE invoiceNumber IN (
                SELECT invoiceNumber FROM invoices
                GROUP BY invoiceNumber HAVING COUNT(*) > 1
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_invoiceNumber
            ON invoices(invoiceNumber)
        """.trimIndent())
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")

        // ── العميل النقدي ────────────────────────────────
        db.execSQL("""
            UPDATE invoices SET clientId = 'cash_client_main'
            WHERE clientId IN (SELECT id FROM clients WHERE name = 'العميل النقدي')
        """.trimIndent())
        db.execSQL("""
            UPDATE payments SET clientId = 'cash_client_main'
            WHERE clientId IN (SELECT id FROM clients WHERE name = 'العميل النقدي')
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO clients
                (id, name, phone, address, workplace, generalNote,
                 clientType, carType, bankAccount, specialty, createdAt)
            SELECT 'cash_client_main', name, phone, address, workplace, generalNote,
                   clientType, carType, bankAccount, specialty, createdAt
            FROM clients WHERE name = 'العميل النقدي' LIMIT 1
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO clients
                (id, name, phone, address, workplace, generalNote,
                 clientType, carType, bankAccount, specialty, createdAt)
            VALUES ('cash_client_main','العميل النقدي','','','','',
                    'INDIVIDUAL','','','',${System.currentTimeMillis()})
        """.trimIndent())
        db.execSQL("DELETE FROM clients WHERE name = 'العميل النقدي' AND id != 'cash_client_main'")

        // ── المورد النقدي ────────────────────────────────
        db.execSQL("""
            UPDATE invoices SET clientId = 'cash_supplier_main'
            WHERE clientId IN (SELECT id FROM clients WHERE name = 'المورد النقدي')
        """.trimIndent())
        db.execSQL("""
            UPDATE payments SET clientId = 'cash_supplier_main'
            WHERE clientId IN (SELECT id FROM clients WHERE name = 'المورد النقدي')
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO clients
                (id, name, phone, address, workplace, generalNote,
                 clientType, carType, bankAccount, specialty, createdAt)
            SELECT 'cash_supplier_main', name, phone, address, workplace, generalNote,
                   clientType, carType, bankAccount, specialty, createdAt
            FROM clients WHERE name = 'المورد النقدي' LIMIT 1
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO clients
                (id, name, phone, address, workplace, generalNote,
                 clientType, carType, bankAccount, specialty, createdAt)
            VALUES ('cash_supplier_main','المورد النقدي','','','','',
                    'SUPPLIER','','','',${System.currentTimeMillis()})
        """.trimIndent())
        db.execSQL("DELETE FROM clients WHERE name = 'المورد النقدي' AND id != 'cash_supplier_main'")

        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

// ✅ Migration 13 → 14
// ① إضافة inventoryItemId لجدول invoice_items
// ② إعادة بناء inventory_movements مع ForeignKey → inventory_items
