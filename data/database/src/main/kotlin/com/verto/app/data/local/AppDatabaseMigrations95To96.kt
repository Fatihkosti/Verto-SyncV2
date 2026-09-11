package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * M03 adds only cutover bookkeeping. Source intents are deliberately not copied by raw schema
 * migration because schema migration has no trusted account/session identity. Runtime preparation
 * performs the tenant-bound census transactionally before V2 cutover.
 */
val MIGRATION_95_96 = object : Migration(95, 96) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_legacy_migration_entry` (
                `organization_id` TEXT NOT NULL,
                `source_kind` TEXT NOT NULL,
                `source_id` TEXT NOT NULL,
                `sync_principal_id` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `source_state` TEXT NOT NULL,
                `business_identity` TEXT NOT NULL,
                `source_sequence` INTEGER,
                `command_batch_id` TEXT,
                `command_order` INTEGER,
                `depends_on_source_id` TEXT,
                `target_kind` TEXT NOT NULL,
                `target_mutation_id` TEXT,
                `disposition` TEXT NOT NULL,
                `reason_code` TEXT,
                `source_fingerprint` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`organization_id`, `source_kind`, `source_id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_legacy_migration_disposition` ON `sync_legacy_migration_entry` (`organization_id`, `disposition`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_legacy_migration_aggregate` ON `sync_legacy_migration_entry` (`organization_id`, `aggregate_type`, `aggregate_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_legacy_migration_target_mutation` ON `sync_legacy_migration_entry` (`target_mutation_id`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_legacy_migration_state` (
                `organization_id` TEXT NOT NULL,
                `sync_principal_id` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `source_count` INTEGER NOT NULL,
                `migrated_count` INTEGER NOT NULL,
                `receipt_confirmed_count` INTEGER NOT NULL,
                `review_count` INTEGER NOT NULL,
                `source_digest` TEXT NOT NULL,
                `legacy_writes_fenced` INTEGER NOT NULL,
                `started_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                PRIMARY KEY(`organization_id`, `sync_principal_id`)
            )
            """.trimIndent()
        )
    }
}
