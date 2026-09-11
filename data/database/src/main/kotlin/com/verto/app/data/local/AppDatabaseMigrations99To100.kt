package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** B11: additive, immutable conflict-review evidence and append-only decision audit. */
val MIGRATION_99_100 = object : Migration(99, 100) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // B11 adds proof-bearing superseded states. Rebuild the v99 table so SQLite's v78 CHECK
        // accepts them while preserving every semantic/lease invariant and every existing row.
        db.execSQL("DROP TRIGGER IF EXISTS `sync_outbox_semantic_immutable`")
        db.execSQL("DROP TRIGGER IF EXISTS `sync_outbox_no_delete`")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_outbox_org_local_sequence`")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_outbox_aggregate_sequence`")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_outbox_delivery`")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_outbox_aggregate_delivery`")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_outbox_dependency`")
        db.execSQL("ALTER TABLE `sync_outbox` RENAME TO `sync_outbox_b11_old`")
        db.execSQL(
            """
            CREATE TABLE `sync_outbox` (
                `mutation_id` TEXT NOT NULL PRIMARY KEY,
                `organization_id` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `operation_type` TEXT NOT NULL,
                `base_version` INTEGER,
                `local_sequence` INTEGER NOT NULL,
                `aggregate_sequence` INTEGER NOT NULL,
                `payload_version` INTEGER NOT NULL,
                `payload_json` TEXT NOT NULL,
                `semantic_fingerprint` TEXT NOT NULL,
                `command_batch_id` TEXT,
                `command_order` INTEGER,
                `depends_on_mutation_id` TEXT,
                `state` TEXT NOT NULL DEFAULT 'PENDING',
                `attempt_count` INTEGER NOT NULL DEFAULT 0,
                `last_error_type` TEXT,
                `last_error_code` TEXT,
                `next_attempt_at` INTEGER NOT NULL DEFAULT 0,
                `lease_owner` TEXT,
                `lease_token` TEXT,
                `lease_scope_epoch` INTEGER,
                `lease_expires_at` INTEGER,
                `acked_server_revision` INTEGER,
                `acked_server_version` INTEGER,
                `receipt_status` TEXT,
                `created_at` INTEGER NOT NULL,
                `acked_at` INTEGER,
                CHECK (trim(`mutation_id`) <> ''),
                CHECK (trim(`organization_id`) <> ''),
                CHECK (trim(`aggregate_type`) <> ''),
                CHECK (trim(`aggregate_id`) <> ''),
                CHECK (`operation_type` IN ('UPSERT','DELETE','COMMAND','ARCHIVE','VOID','REVERSE','CANCEL')),
                CHECK (`local_sequence` > 0),
                CHECK (`aggregate_sequence` > 0),
                CHECK (`payload_version` > 0),
                CHECK (length(CAST(`payload_json` AS BLOB)) <= 524288),
                CHECK (trim(`semantic_fingerprint`) <> ''),
                CHECK (`attempt_count` >= 0),
                CHECK ((`command_batch_id` IS NULL AND `command_order` IS NULL) OR (trim(`command_batch_id`) <> '' AND `command_order` >= 0)),
                CHECK (`depends_on_mutation_id` IS NULL OR (trim(`depends_on_mutation_id`) <> '' AND `depends_on_mutation_id` <> `mutation_id`)),
                CHECK (`state` IN ('PENDING','LEASED','RETRY','ACKNOWLEDGED','REQUIRES_REVIEW','REJECTED','SUPERSEDED_PENDING_PROOF','SUPERSEDED_WITH_PROOF')),
                CHECK ((`state` = 'LEASED' AND trim(`lease_owner`) <> '' AND trim(`lease_token`) <> '' AND `lease_expires_at` IS NOT NULL)
                    OR (`state` <> 'LEASED' AND `lease_owner` IS NULL AND `lease_token` IS NULL AND `lease_expires_at` IS NULL))
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `sync_outbox` (
                mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,base_version,
                local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,
                command_batch_id,command_order,depends_on_mutation_id,state,attempt_count,last_error_type,
                last_error_code,next_attempt_at,lease_owner,lease_token,lease_scope_epoch,lease_expires_at,
                acked_server_revision,acked_server_version,receipt_status,created_at,acked_at
            ) SELECT
                mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,base_version,
                local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,
                command_batch_id,command_order,depends_on_mutation_id,state,attempt_count,last_error_type,
                last_error_code,next_attempt_at,lease_owner,lease_token,lease_scope_epoch,lease_expires_at,
                acked_server_revision,acked_server_version,receipt_status,created_at,acked_at
            FROM `sync_outbox_b11_old`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `sync_outbox_b11_old`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_org_local_sequence` ON `sync_outbox` (`organization_id`, `local_sequence`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_aggregate_sequence` ON `sync_outbox` (`organization_id`, `aggregate_type`, `aggregate_id`, `aggregate_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_delivery` ON `sync_outbox` (`organization_id`, `state`, `next_attempt_at`, `local_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_aggregate_delivery` ON `sync_outbox` (`organization_id`, `aggregate_type`, `aggregate_id`, `state`, `aggregate_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_dependency` ON `sync_outbox` (`depends_on_mutation_id`)")
        db.execSQL(
            """
            CREATE TRIGGER `sync_outbox_semantic_immutable`
            BEFORE UPDATE OF `mutation_id`,`organization_id`,`aggregate_type`,`aggregate_id`,`operation_type`,`base_version`,`local_sequence`,`aggregate_sequence`,`payload_version`,`payload_json`,`semantic_fingerprint`,`command_batch_id`,`command_order`,`depends_on_mutation_id`,`created_at`
            ON `sync_outbox`
            BEGIN SELECT RAISE(ABORT, 'FAIL_LOCAL_SYNC_IMMUTABILITY'); END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER `sync_outbox_no_delete`
            BEFORE DELETE ON `sync_outbox`
            BEGIN SELECT RAISE(ABORT, 'SYNC_OUTBOX_PRUNING_DISABLED_V306'); END
            """.trimIndent()
        )

        // B11 replacement intent points at the superseded mutation without abusing causal dependency.
        db.execSQL("ALTER TABLE sync_mutation_packet ADD COLUMN supersedes_mutation_id TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_mutation_packet_supersedes ON sync_mutation_packet (supersedes_mutation_id)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_conflict_review_evidence` (
                `conflict_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `mutation_id` TEXT NOT NULL,
                `local_payload_json` TEXT NOT NULL,
                `local_payload_sha256` TEXT NOT NULL,
                `local_semantic_fingerprint` TEXT NOT NULL,
                `local_base_version` INTEGER,
                `remote_payload_json` TEXT NOT NULL,
                `remote_payload_sha256` TEXT NOT NULL,
                `server_revision` INTEGER,
                `server_version` INTEGER NOT NULL,
                `outcome_proof` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`conflict_id`),
                CHECK(trim(`organization_id`) <> ''),
                CHECK(trim(`mutation_id`) <> ''),
                CHECK(length(`local_payload_sha256`) = 64),
                CHECK(length(`remote_payload_sha256`) = 64),
                CHECK(`server_version` > 0),
                CHECK(`outcome_proof` IN ('PROVEN_CONFLICT','OUTCOME_UNKNOWN'))
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_review_evidence_org_created` ON `sync_conflict_review_evidence` (`organization_id`,`created_at`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflict_review_evidence_mutation` ON `sync_conflict_review_evidence` (`mutation_id`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_conflict_resolution_audit` (
                `decision_id` TEXT NOT NULL,
                `conflict_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `mutation_id` TEXT NOT NULL,
                `decision_type` TEXT NOT NULL,
                `actor_id` TEXT,
                `actor_role` TEXT,
                `local_payload_sha256` TEXT NOT NULL,
                `remote_payload_sha256` TEXT NOT NULL,
                `expected_server_version` INTEGER NOT NULL,
                `resolution_mutation_id` TEXT,
                `proof_type` TEXT NOT NULL,
                `proof_reference` TEXT,
                `before_state` TEXT NOT NULL,
                `after_state` TEXT NOT NULL,
                `decided_at` INTEGER NOT NULL,
                PRIMARY KEY(`decision_id`),
                CHECK(trim(`organization_id`) <> ''),
                CHECK(trim(`mutation_id`) <> ''),
                CHECK(length(`local_payload_sha256`) = 64),
                CHECK(length(`remote_payload_sha256`) = 64),
                CHECK(`expected_server_version` > 0)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_resolution_audit_conflict_time` ON `sync_conflict_resolution_audit` (`conflict_id`,`decided_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflict_resolution_audit_resolution_mutation` ON `sync_conflict_resolution_audit` (`resolution_mutation_id`)")
        installB11ConflictIntegrityGuards(db)
    }
}

/** Ensures fresh schema-100 installs get the same append-only guards as 99→100 upgrades. */
internal fun installB11ConflictIntegrityGuards(db: SupportSQLiteDatabase) {
    // B11 decisions depend on the original local intent remaining byte-stable on fresh installs too.
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_semantic_immutable`
        BEFORE UPDATE OF `mutation_id`,`organization_id`,`aggregate_type`,`aggregate_id`,`operation_type`,`base_version`,`local_sequence`,`aggregate_sequence`,`payload_version`,`payload_json`,`semantic_fingerprint`,`command_batch_id`,`command_order`,`depends_on_mutation_id`,`created_at`
        ON `sync_outbox`
        BEGIN SELECT RAISE(ABORT, 'FAIL_LOCAL_SYNC_IMMUTABILITY'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_no_delete`
        BEFORE DELETE ON `sync_outbox`
        BEGIN SELECT RAISE(ABORT, 'SYNC_OUTBOX_PRUNING_DISABLED_V306'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_conflict_review_evidence_immutable`
        BEFORE UPDATE ON `sync_conflict_review_evidence`
        BEGIN SELECT RAISE(ABORT, 'FAIL_CONFLICT_EVIDENCE_IMMUTABLE'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_conflict_review_evidence_no_delete`
        BEFORE DELETE ON `sync_conflict_review_evidence`
        BEGIN SELECT RAISE(ABORT, 'FAIL_CONFLICT_EVIDENCE_IMMUTABLE'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_conflict_resolution_audit_immutable`
        BEFORE UPDATE ON `sync_conflict_resolution_audit`
        BEGIN SELECT RAISE(ABORT, 'FAIL_CONFLICT_AUDIT_IMMUTABLE'); END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_conflict_resolution_audit_no_delete`
        BEFORE DELETE ON `sync_conflict_resolution_audit`
        BEGIN SELECT RAISE(ABORT, 'FAIL_CONFLICT_AUDIT_IMMUTABLE'); END
        """.trimIndent()
    )
}
