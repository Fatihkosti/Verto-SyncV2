package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds tenant/user-scoped Home ordering and pending-event interaction state. */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS home_quick_action_order (
                organization_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                action_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, user_id, action_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_home_quick_action_order_scope_position
            ON home_quick_action_order(organization_id, user_id, position)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_home_quick_action_order_scope
            ON home_quick_action_order(organization_id, user_id)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS home_event_states (
                organization_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                event_key TEXT NOT NULL,
                seen_at INTEGER,
                snoozed_until INTEGER,
                dismissed_at INTEGER,
                PRIMARY KEY(organization_id, user_id, event_key)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_home_event_states_scope_snoozed_until
            ON home_event_states(organization_id, user_id, snoozed_until)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_home_event_states_scope_dismissed_at
            ON home_event_states(organization_id, user_id, dismissed_at)
            """.trimIndent(),
        )
    }
}
