package com.verto.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** B12 audit facts are append-only on both upgraded and fresh databases. */
internal fun installB12ExpenseRevisionIntegrityGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `expense_revision_history_immutable_b12`
        BEFORE UPDATE ON `expense_revision_history`
        BEGIN SELECT RAISE(ABORT, 'FAIL_EXPENSE_REVISION_HISTORY_IMMUTABLE'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `expense_revision_history_no_delete_b12`
        BEFORE DELETE ON `expense_revision_history`
        BEGIN SELECT RAISE(ABORT, 'FAIL_EXPENSE_REVISION_HISTORY_IMMUTABLE'); END
        """.trimIndent()
    )
}
