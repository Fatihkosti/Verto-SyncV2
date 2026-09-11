package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F251: fixed-point source of truth for advance client/supplier credit created by bulk invoice payments. */
val MIGRATION_67_68 = object : Migration(67, 68) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE client_credits ADD COLUMN amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE client_credits SET amount_minor = CAST(ROUND(amount * 100.0) AS INTEGER)")

        val max = 9_000_000_000_000_000L
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS guard_client_credits_amount_minor_insert " +
                "BEFORE INSERT ON client_credits " +
                "WHEN NEW.amount_minor < -$max OR NEW.amount_minor > $max " +
                "BEGIN SELECT RAISE(ABORT, 'money minor value out of range'); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS guard_client_credits_amount_minor_update " +
                "BEFORE UPDATE OF amount_minor ON client_credits " +
                "WHEN NEW.amount_minor < -$max OR NEW.amount_minor > $max " +
                "BEGIN SELECT RAISE(ABORT, 'money minor value out of range'); END"
        )
    }
}
