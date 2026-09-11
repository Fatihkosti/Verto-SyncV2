package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v259: local stock command idempotency + transactional inventory outbox. */
val MIGRATION_74_75 = object : Migration(74, 75) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_write_guards` (
                `id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `command_id` TEXT NOT NULL,
                `idempotency_key` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `actor_id` TEXT NOT NULL,
                `actor_name` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_write_guard_command` ON `inventory_write_guards` (`organization_id`,`command_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_write_guard_idempotency` ON `inventory_write_guards` (`organization_id`,`idempotency_key`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_stock_outbox` (
                `id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `command_id` TEXT NOT NULL,
                `idempotency_key` TEXT NOT NULL,
                `movement_id` TEXT NOT NULL,
                `item_id` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `signed_base_quantity` INTEGER NOT NULL,
                `sync_state` TEXT NOT NULL DEFAULT 'PENDING',
                `attempt_count` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_stock_outbox_movement` ON `inventory_stock_outbox` (`organization_id`,`movement_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_stock_outbox_command` ON `inventory_stock_outbox` (`organization_id`,`command_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_stock_outbox_delivery` ON `inventory_stock_outbox` (`organization_id`,`sync_state`,`created_at`)")
    }
}
