package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 335: bounded finance queries + retirement of invalid client-owned cash-register commands. */
val MIGRATION_82_83 = object : Migration(82, 83) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_lifecycle_date` ON `expenses` (`lifecycle_state`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_lifecycle_category_date` ON `expenses` (`lifecycle_state`, `category`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_register_movements_type_reference` ON `cash_register_movements` (`movementType`, `referenceId`)")
        db.execSQL(
            """
            UPDATE `sync_outbox`
               SET `state` = 'REJECTED',
                   `last_error_type` = 'VALIDATION',
                   `last_error_code` = 'SERVER_AUTHORITATIVE_NO_CLIENT_PUSH',
                   `lease_owner` = NULL,
                   `lease_token` = NULL,
                   `lease_expires_at` = NULL
             WHERE `aggregate_type` = 'CASH_REGISTER'
               AND `state` IN ('PENDING', 'LEASED', 'RETRY', 'REQUIRES_REVIEW')
            """.trimIndent()
        )
    }
}
