package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 346: supplier intelligence needs an explicit immutable PO delivery promise. */
val MIGRATION_83_84 = object : Migration(83, 84) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `purchase_orders` ADD COLUMN `promised_delivery_at` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplier_promised_delivery` " +
                "ON `purchase_orders` (`supplier_id`, `promised_delivery_at`)"
        )
    }
}
