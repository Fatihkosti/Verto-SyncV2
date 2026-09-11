package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F249: durable financial Outbox/Inbox with aggregate ordering and conflict-safe delivery state. */
val MIGRATION_66_67 = object : Migration(66, 67) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `financial_outbox` (
                `event_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `write_id` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `aggregate_version` INTEGER NOT NULL,
                `sequence` INTEGER NOT NULL,
                `operation_type` TEXT NOT NULL,
                `payload_version` INTEGER NOT NULL DEFAULT 1,
                `schema_version` INTEGER NOT NULL DEFAULT 1,
                `payload` TEXT NOT NULL,
                `occurred_at` INTEGER NOT NULL,
                `recorded_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `sync_state` TEXT NOT NULL DEFAULT 'PENDING',
                `attempt_count` INTEGER NOT NULL DEFAULT 0,
                `next_attempt_at` INTEGER NOT NULL DEFAULT 0,
                `last_error` TEXT NOT NULL DEFAULT '',
                `server_revision` INTEGER,
                `synced_at` INTEGER,
                PRIMARY KEY(`event_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_outbox_identity` " +
                "ON `financial_outbox` (`organization_id`, `operation_type`, `write_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_outbox_aggregate_sequence` " +
                "ON `financial_outbox` (`organization_id`, `aggregate_id`, `sequence`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_financial_outbox_delivery` " +
                "ON `financial_outbox` (`organization_id`, `sync_state`, `next_attempt_at`, `created_at`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `financial_inbox` (
                `event_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `aggregate_version` INTEGER NOT NULL,
                `sequence` INTEGER NOT NULL,
                `operation_type` TEXT NOT NULL,
                `payload_version` INTEGER NOT NULL,
                `schema_version` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `occurred_at` INTEGER NOT NULL,
                `recorded_at` INTEGER NOT NULL,
                `server_revision` INTEGER NOT NULL,
                `apply_state` TEXT NOT NULL,
                `apply_reason` TEXT NOT NULL DEFAULT '',
                `received_at` INTEGER NOT NULL,
                `applied_at` INTEGER,
                PRIMARY KEY(`event_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_inbox_server_revision` " +
                "ON `financial_inbox` (`organization_id`, `server_revision`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_financial_inbox_aggregate_state` " +
                "ON `financial_inbox` (`organization_id`, `aggregate_id`, `apply_state`)"
        )
    }
}
