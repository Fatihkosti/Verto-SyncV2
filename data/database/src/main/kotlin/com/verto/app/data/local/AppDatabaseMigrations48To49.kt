package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds tenant-scoped, audience-filtered educational topics with local-first dirty tracking. */
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS educational_topics (
                organization_id TEXT NOT NULL,
                topic_id TEXT NOT NULL,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                full_content TEXT NOT NULL,
                category TEXT NOT NULL,
                is_active INTEGER NOT NULL,
                created_by_user_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_dirty INTEGER NOT NULL,
                deleted_at INTEGER,
                PRIMARY KEY(organization_id, topic_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topics_organization_id ON educational_topics(organization_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topics_org_active_deleted ON educational_topics(organization_id, is_active, deleted_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topics_dirty ON educational_topics(is_dirty)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS educational_topic_targets (
                organization_id TEXT NOT NULL,
                topic_id TEXT NOT NULL,
                target_type TEXT NOT NULL,
                target_value TEXT NOT NULL,
                PRIMARY KEY(organization_id, topic_id, target_type, target_value),
                FOREIGN KEY(organization_id, topic_id)
                    REFERENCES educational_topics(organization_id, topic_id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topic_targets_organization_id ON educational_topic_targets(organization_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topic_targets_topic_id ON educational_topic_targets(topic_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topic_targets_type ON educational_topic_targets(target_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topic_targets_value ON educational_topic_targets(target_value)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_educational_topic_targets_lookup ON educational_topic_targets(organization_id, target_type, target_value, topic_id)")
    }
}
