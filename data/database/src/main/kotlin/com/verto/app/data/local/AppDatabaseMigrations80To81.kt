package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 313: additive recovery/bootstrap durability only; no domain table mutation. */
val MIGRATION_80_81 = object : Migration(80, 81) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_recovery_state` (
                `scope_id` TEXT NOT NULL, `organization_id` TEXT NOT NULL, `sync_principal_id` TEXT NOT NULL,
                `contract_family` TEXT NOT NULL, `contract_version` INTEGER NOT NULL, `scope_definition_version` INTEGER NOT NULL,
                `state` TEXT NOT NULL, `reason` TEXT NOT NULL, `bootstrap_session_id` TEXT, `baseline_cursor` TEXT,
                `baseline_revision` INTEGER, `next_page_token` TEXT, `expected_snapshot_rows` INTEGER,
                `staged_snapshot_rows` INTEGER NOT NULL DEFAULT 0, `recovery_generation` INTEGER NOT NULL DEFAULT 0,
                `attempt_count` INTEGER NOT NULL DEFAULT 0, `last_error_code` TEXT, `started_at` INTEGER,
                `updated_at` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`scope_id`), CHECK (`state` IN ('NOT_STARTED','IN_PROGRESS','READY','RECOVERY_REQUIRED')), CHECK (`contract_version` > 0), CHECK (`scope_definition_version` > 0)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_bootstrap_stage` (
                `scope_id` TEXT NOT NULL, `bootstrap_session_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL,
                `aggregate_type` TEXT NOT NULL, `aggregate_id` TEXT NOT NULL, `entity_version` INTEGER,
                `payload_version` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, `partition_key` TEXT NOT NULL,
                `content_fingerprint` TEXT NOT NULL,
                PRIMARY KEY(`scope_id`, `bootstrap_session_id`, `ordinal`), CHECK (`ordinal` > 0), CHECK (`payload_version` > 0), CHECK (length(`aggregate_type`) > 0), CHECK (length(`aggregate_id`) > 0), CHECK (length(`partition_key`) > 0), CHECK (length(`content_fingerprint`) > 0)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_bootstrap_stage_identity` ON `sync_bootstrap_stage` (`scope_id`,`bootstrap_session_id`,`aggregate_type`,`aggregate_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_bootstrap_stage_aggregate` ON `sync_bootstrap_stage` (`scope_id`,`bootstrap_session_id`,`aggregate_type`,`ordinal`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_health_state` (
                `scope_id` TEXT NOT NULL, `organization_id` TEXT NOT NULL,
                `last_successful_push_at` INTEGER, `last_successful_pull_at` INTEGER,
                `last_failure_category` TEXT, `last_failure_code` TEXT, `last_reconciliation_at` INTEGER,
                `last_reconciliation_status` TEXT, `last_observed_server_revision` INTEGER,
                `full_resync_count` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`scope_id`)
            )
        """.trimIndent())
    }
}
