package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session 354: free-form team observations captured on Home and reviewed by managers. */
val MIGRATION_86_87 = object : Migration(86, 87) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `team_observations` (
                `organization_id` TEXT NOT NULL,
                `observation_id` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `author_user_id` TEXT NOT NULL,
                `author_name` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `is_important` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `updated_by_user_id` TEXT NOT NULL,
                `is_dirty` INTEGER NOT NULL,
                PRIMARY KEY(`organization_id`, `observation_id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_observations_org_created` ON `team_observations` (`organization_id`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_observations_org_status_important` ON `team_observations` (`organization_id`, `status`, `is_important`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_observations_author` ON `team_observations` (`organization_id`, `author_user_id`, `created_at`)")
    }
}
