package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session v60: generic Local-first messaging storage plus tenant-scoped Optimal binding. */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_conversations (
                organization_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                subject TEXT NOT NULL,
                is_archived INTEGER NOT NULL,
                unread_count INTEGER NOT NULL,
                last_message_preview TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_activity_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, conversation_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_message_conversations_org_client_activity
            ON message_conversations(organization_id, client_id, last_activity_at)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_message_conversations_org_archived_activity
            ON message_conversations(organization_id, is_archived, last_activity_at)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                organization_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                remote_id TEXT,
                sender_id TEXT NOT NULL,
                sender_name_snapshot TEXT NOT NULL,
                sender_role_snapshot TEXT NOT NULL,
                kind TEXT NOT NULL,
                body TEXT NOT NULL,
                delivery_status TEXT NOT NULL,
                is_read INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, message_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_messages_org_conversation_created_at
            ON messages(organization_id, conversation_id, created_at)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_messages_org_conversation_read
            ON messages(organization_id, conversation_id, is_read)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_messages_org_client_created_at
            ON messages(organization_id, client_id, created_at)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_messages_org_remote_id
            ON messages(organization_id, remote_id)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_media (
                organization_id TEXT NOT NULL,
                media_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                local_uri TEXT,
                remote_url TEXT,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                duration_ms INTEGER,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, media_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_message_media_org_message
            ON message_media(organization_id, message_id)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_conversation_bindings (
                organization_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                bound_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, client_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_conversation_bindings_org_conversation
            ON optimal_conversation_bindings(organization_id, conversation_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_conversation_bindings_org_bound_at
            ON optimal_conversation_bindings(organization_id, bound_at)
            """.trimIndent(),
        )
    }
}
