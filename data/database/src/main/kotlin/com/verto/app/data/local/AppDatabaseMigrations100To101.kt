package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** B13: durable bootstrap seal, staged promotion states and protection-content manifest. */
val MIGRATION_100_101 = object : Migration(100, 101) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_recovery_state ADD COLUMN expected_snapshot_digest TEXT")
        db.execSQL("ALTER TABLE sync_recovery_state ADD COLUMN expected_coverage_json TEXT")
        db.execSQL("ALTER TABLE sync_recovery_state ADD COLUMN bootstrap_high_watermark INTEGER")
        db.execSQL("ALTER TABLE sync_recovery_state ADD COLUMN bootstrap_delta_token TEXT")
        db.execSQL("ALTER TABLE sync_recovery_state ADD COLUMN stage_verified_at INTEGER")

        db.execSQL("ALTER TABLE sync_bootstrap_stage ADD COLUMN is_tombstone INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_bootstrap_stage ADD COLUMN promotion_state TEXT NOT NULL DEFAULT 'STAGED'")
        db.execSQL("ALTER TABLE sync_bootstrap_stage ADD COLUMN wait_reason TEXT")
        db.execSQL("ALTER TABLE sync_bootstrap_stage ADD COLUMN applied_at INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_recovery_protection_manifest (
                scope_id TEXT NOT NULL,
                bootstrap_session_id TEXT NOT NULL,
                organization_id TEXT NOT NULL,
                combined_sha256 TEXT NOT NULL,
                unified_sha256 TEXT NOT NULL,
                party_sha256 TEXT NOT NULL,
                financial_sha256 TEXT NOT NULL,
                inventory_stock_sha256 TEXT NOT NULL,
                inventory_cost_sha256 TEXT NOT NULL,
                optimal_sha256 TEXT NOT NULL,
                attachment_sha256 TEXT NOT NULL,
                pending_reference_sha256 TEXT NOT NULL,
                mutation_packet_sha256 TEXT NOT NULL,
                local_generation_sha256 TEXT NOT NULL,
                captured_at INTEGER NOT NULL,
                PRIMARY KEY(scope_id, bootstrap_session_id),
                CHECK(length(combined_sha256)=64),
                CHECK(length(unified_sha256)=64),
                CHECK(length(party_sha256)=64),
                CHECK(length(financial_sha256)=64),
                CHECK(length(inventory_stock_sha256)=64),
                CHECK(length(inventory_cost_sha256)=64),
                CHECK(length(optimal_sha256)=64),
                CHECK(length(attachment_sha256)=64),
                CHECK(length(pending_reference_sha256)=64),
                CHECK(length(mutation_packet_sha256)=64),
                CHECK(length(local_generation_sha256)=64)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_recovery_protection_manifest_org ON sync_recovery_protection_manifest (organization_id)")
    }
}
