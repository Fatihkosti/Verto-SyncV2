package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Session v56: tenant-scoped Optimal company links, registration-code cache, and durable outbox. */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_company_links (
                organization_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                optimal_company_id TEXT NOT NULL,
                linked_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, client_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_company_links_org_company
            ON optimal_company_links(organization_id, optimal_company_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_company_links_org_linked_at
            ON optimal_company_links(organization_id, linked_at)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_registration_codes (
                organization_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                code TEXT NOT NULL,
                issued_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                used_at INTEGER,
                PRIMARY KEY(organization_id, client_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_registration_codes_org_code
            ON optimal_registration_codes(organization_id, code)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_registration_codes_org_expires_at
            ON optimal_registration_codes(organization_id, expires_at)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_outbox (
                organization_id TEXT NOT NULL,
                event_id TEXT NOT NULL,
                aggregate_type TEXT NOT NULL,
                aggregate_id TEXT NOT NULL,
                operation TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                payload_version INTEGER NOT NULL,
                idempotency_key TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                status TEXT NOT NULL,
                attempt_count INTEGER NOT NULL,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, event_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_outbox_org_idempotency
            ON optimal_outbox(organization_id, idempotency_key)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_outbox_org_aggregate_sequence
            ON optimal_outbox(organization_id, aggregate_type, aggregate_id, sequence)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_outbox_org_status_created_at
            ON optimal_outbox(organization_id, status, created_at)
            """.trimIndent(),
        )
    }
}
