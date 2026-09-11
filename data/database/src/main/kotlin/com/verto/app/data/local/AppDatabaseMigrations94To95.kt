package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v388 customer taxonomy closeout.
 *
 * CustomerProfile.segment is reduced to the six approved values. COMPETITOR is no longer a
 * segment; competitor behavior is derived from having both active CUSTOMER and SUPPLIER roles.
 * Unknown/legacy classifications are normalized deterministically so old installs remain readable.
 */
val MIGRATION_94_95 = object : Migration(94, 95) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE customer_profiles
               SET segment = CASE
                   WHEN segment IN ('INDIVIDUAL','CAR_OWNER','OTHER') THEN 'INDIVIDUAL'
                   WHEN segment IN ('COMPANY','INSTITUTION') THEN 'COMPANY'
                   WHEN segment IN ('WORKSHOP_OWNER','MECHANIC') THEN 'WORKSHOP_OWNER'
                   WHEN segment = 'MARKETER' THEN 'MARKETER'
                   WHEN segment IN ('TRADER','SHOP_OWNER','COMPETITOR') THEN 'TRADER'
                   WHEN segment IN ('DISTRIBUTOR','WHOLESALE_TRADER') THEN 'DISTRIBUTOR'
                   ELSE 'INDIVIDUAL'
               END
            """.trimIndent()
        )
    }
}
