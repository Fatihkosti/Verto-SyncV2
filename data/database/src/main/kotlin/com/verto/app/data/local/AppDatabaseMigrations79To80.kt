package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 309: additive durable conflict state only. */
val MIGRATION_79_80 = object : Migration(79, 80) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_conflict` (
                `conflict_id` TEXT NOT NULL,
                `mutation_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `conflict_code` TEXT NOT NULL,
                `local_payload_version` INTEGER NOT NULL,
                `server_version` INTEGER NOT NULL,
                `authoritative_payload_json` TEXT NOT NULL,
                `resolution_requirement` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `request_hash` TEXT,
                `resolution_mutation_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `resolved_at` INTEGER,
                PRIMARY KEY(`conflict_id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflict_mutation_id` ON `sync_conflict` (`mutation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_org_state_created` ON `sync_conflict` (`organization_id`, `state`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_aggregate_state` ON `sync_conflict` (`organization_id`, `aggregate_type`, `aggregate_id`, `state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_resolution_mutation` ON `sync_conflict` (`resolution_mutation_id`)")
    }
}
