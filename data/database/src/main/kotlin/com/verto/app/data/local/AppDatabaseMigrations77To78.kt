package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_77_78 = object : Migration(77, 78) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
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
                CHECK (`state` IN ('PENDING','LEASED','RETRY','ACKNOWLEDGED','REQUIRES_REVIEW','REJECTED')),
                CHECK ((`state` = 'LEASED' AND trim(`lease_owner`) <> '' AND trim(`lease_token`) <> '' AND `lease_expires_at` IS NOT NULL)
                    OR (`state` <> 'LEASED' AND `lease_owner` IS NULL AND `lease_token` IS NULL AND `lease_expires_at` IS NULL))
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_org_local_sequence` ON `sync_outbox` (`organization_id`, `local_sequence`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_aggregate_sequence` ON `sync_outbox` (`organization_id`, `aggregate_type`, `aggregate_id`, `aggregate_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_delivery` ON `sync_outbox` (`organization_id`, `state`, `next_attempt_at`, `local_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_aggregate_delivery` ON `sync_outbox` (`organization_id`, `aggregate_type`, `aggregate_id`, `state`, `aggregate_sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_dependency` ON `sync_outbox` (`depends_on_mutation_id`)")
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `sync_outbox_semantic_immutable`
            BEFORE UPDATE OF `mutation_id`,`organization_id`,`aggregate_type`,`aggregate_id`,`operation_type`,`base_version`,`local_sequence`,`aggregate_sequence`,`payload_version`,`payload_json`,`semantic_fingerprint`,`command_batch_id`,`command_order`,`depends_on_mutation_id`,`created_at`
            ON `sync_outbox`
            BEGIN
                SELECT RAISE(ABORT, 'FAIL_LOCAL_SYNC_IMMUTABILITY');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `sync_outbox_no_delete`
            BEFORE DELETE ON `sync_outbox`
            BEGIN
                SELECT RAISE(ABORT, 'SYNC_OUTBOX_PRUNING_DISABLED_V306');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_inbox` (
                `scope_id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `server_revision` INTEGER NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `operation_type` TEXT NOT NULL,
                `entity_version` INTEGER,
                `payload_version` INTEGER NOT NULL,
                `payload_json` TEXT NOT NULL,
                `origin_mutation_id` TEXT,
                `transaction_id` TEXT NOT NULL,
                `transaction_order` INTEGER NOT NULL,
                `transaction_size` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `changed_at` INTEGER NOT NULL,
                `content_fingerprint` TEXT NOT NULL,
                `apply_state` TEXT NOT NULL DEFAULT 'RECEIVED',
                `apply_error_code` TEXT,
                `received_at` INTEGER NOT NULL,
                `applied_at` INTEGER,
                PRIMARY KEY (`scope_id`, `server_revision`),
                CHECK (trim(`scope_id`) <> ''),
                CHECK (trim(`organization_id`) <> ''),
                CHECK (`server_revision` >= 0),
                CHECK (trim(`aggregate_type`) <> ''),
                CHECK (trim(`aggregate_id`) <> ''),
                CHECK (`operation_type` IN ('UPSERT','DELETE','COMMAND','ARCHIVE','VOID','REVERSE','CANCEL')),
                CHECK (`payload_version` > 0),
                CHECK (length(CAST(`payload_json` AS BLOB)) <= 524288),
                CHECK (trim(`transaction_id`) <> ''),
                CHECK (`transaction_size` >= 1),
                CHECK (`transaction_order` >= 0 AND `transaction_order` < `transaction_size`),
                CHECK (trim(`content_fingerprint`) <> ''),
                CHECK (`apply_state` IN ('RECEIVED','APPLIED','REQUIRES_REVIEW'))
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_inbox_org_scope_revision` ON `sync_inbox` (`organization_id`, `scope_id`, `server_revision`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_inbox_apply` ON `sync_inbox` (`scope_id`, `apply_state`, `server_revision`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_inbox_transaction` ON `sync_inbox` (`scope_id`, `transaction_id`, `transaction_order`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_inbox_aggregate_revision` ON `sync_inbox` (`organization_id`, `aggregate_type`, `aggregate_id`, `server_revision`)")
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `sync_inbox_semantic_immutable`
            BEFORE UPDATE OF `scope_id`,`organization_id`,`server_revision`,`aggregate_type`,`aggregate_id`,`operation_type`,`entity_version`,`payload_version`,`payload_json`,`origin_mutation_id`,`transaction_id`,`transaction_order`,`transaction_size`,`deleted_at`,`changed_at`,`content_fingerprint`,`received_at`
            ON `sync_inbox`
            BEGIN
                SELECT RAISE(ABORT, 'FAIL_LOCAL_SYNC_IMMUTABILITY');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `sync_inbox_no_delete`
            BEFORE DELETE ON `sync_inbox`
            BEGIN
                SELECT RAISE(ABORT, 'SYNC_INBOX_PRUNING_DISABLED_V306');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_cursor` (
                `scope_id` TEXT NOT NULL PRIMARY KEY,
                `organization_id` TEXT NOT NULL,
                `sync_principal_id` TEXT NOT NULL,
                `contract_family` TEXT NOT NULL,
                `contract_version` INTEGER NOT NULL,
                `scope_definition_version` INTEGER NOT NULL,
                `cursor_token` TEXT NOT NULL,
                `last_applied_change_revision` INTEGER,
                `page_high_watermark` INTEGER,
                `min_available_revision` INTEGER,
                `state` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                CHECK (trim(`scope_id`) <> ''),
                CHECK (trim(`organization_id`) <> ''),
                CHECK (trim(`sync_principal_id`) <> ''),
                CHECK (trim(`contract_family`) <> ''),
                CHECK (`contract_version` > 0),
                CHECK (`scope_definition_version` > 0),
                CHECK (trim(`cursor_token`) <> ''),
                CHECK (`state` IN ('ACTIVE','BOOTSTRAP_REQUIRED','INVALIDATED'))
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_sequence_state` (
                `organization_id` TEXT NOT NULL,
                `counter_kind` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL DEFAULT '',
                `aggregate_id` TEXT NOT NULL DEFAULT '',
                `last_value` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY (`organization_id`, `counter_kind`, `aggregate_type`, `aggregate_id`),
                CHECK (trim(`organization_id`) <> ''),
                CHECK (`counter_kind` IN ('GLOBAL','AGGREGATE')),
                CHECK (`last_value` >= 0),
                CHECK ((`counter_kind` = 'GLOBAL' AND `aggregate_type` = '' AND `aggregate_id` = '')
                    OR (`counter_kind` = 'AGGREGATE' AND trim(`aggregate_type`) <> '' AND trim(`aggregate_id`) <> ''))
            )
            """.trimIndent()
        )
    }
}
