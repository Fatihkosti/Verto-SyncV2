package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE join_codes ADD COLUMN employeeName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE join_codes ADD COLUMN jobTitle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE join_codes ADD COLUMN actualJoinDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE join_codes ADD COLUMN usedAt INTEGER")
        db.execSQL("ALTER TABLE join_codes ADD COLUMN usedByUserId TEXT")
    }
}

// Employee Performance Binding: نسبة الفواتير والعملاء للموظف المنشئ (createdBy).
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE clients ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
    }
}

// Notification navigation route cache.
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("notifications", "navigationRoute")) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN navigationRoute TEXT")
        }
    }
}

// Session 4: سجل الرصيد المقدَّم (الفائض من السداد الجماعي).
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS client_credits (
                id TEXT PRIMARY KEY NOT NULL,
                clientId TEXT NOT NULL,
                amount REAL NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                sourcePaymentId TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                employeeId TEXT NOT NULL DEFAULT '',
                employeeName TEXT NOT NULL DEFAULT '',
                isDirty INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_client_credits_clientId ON client_credits(clientId)")
    }
}

// Session 6: إلغاء الفاتورة (soft-delete). عمود voided على invoices.
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN voided INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Session 7 — عكس الدفعة بدل الحذف.
 * عمود يربط الحركة العكسية (amount سالب) بالدفعة الأصلية. الدفعات العادية تبقى NULL.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payments ADD COLUMN reversedPaymentId TEXT")
    }
}
