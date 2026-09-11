package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 307: additive producer durability support. Historical migrations remain untouched. */
val MIGRATION_78_79 = object : Migration(78, 79) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `organization_settings_local` (
                `organization_id` TEXT NOT NULL,
                `shop_name` TEXT NOT NULL,
                `shop_phone` TEXT NOT NULL,
                `city` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `invoice_footer` TEXT NOT NULL,
                `tax_number` TEXT NOT NULL,
                `logo_url` TEXT NOT NULL,
                `signature_url` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`organization_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_attachment_transfer` (
                `transfer_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `mutation_id` TEXT,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `local_uri` TEXT NOT NULL,
                `object_key` TEXT NOT NULL,
                `content_checksum` TEXT NOT NULL,
                `mime_type` TEXT,
                `byte_size` INTEGER,
                `state` TEXT NOT NULL DEFAULT 'PENDING',
                `attempt_count` INTEGER NOT NULL DEFAULT 0,
                `lease_owner` TEXT,
                `lease_token` TEXT,
                `lease_expires_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                PRIMARY KEY(`transfer_id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_delivery` ON `sync_attachment_transfer` (`organization_id`, `state`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_mutation` ON `sync_attachment_transfer` (`mutation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_aggregate` ON `sync_attachment_transfer` (`organization_id`, `aggregate_type`, `aggregate_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_attachment_transfer_object_key` ON `sync_attachment_transfer` (`object_key`)")

        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `lifecycle_state` TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `voided_at` INTEGER")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `void_reason` TEXT")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `reversal_write_id` TEXT")
    }
}
