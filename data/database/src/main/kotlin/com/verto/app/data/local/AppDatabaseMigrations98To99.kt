package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** B10: additive receive/apply metadata. No domain/pending row, packet or inbox event is removed. */
val MIGRATION_98_99 = object : Migration(98, 99) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `sync_inbox_b10_new` (
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
                CHECK (length(CAST(`payload_json` AS BLOB)) <= 2097152),
                CHECK (trim(`transaction_id`) <> ''),
                CHECK (`transaction_size` >= 1),
                CHECK (`transaction_order` >= 0 AND `transaction_order` < `transaction_size`),
                CHECK (trim(`content_fingerprint`) <> ''),
                CHECK (`apply_state` IN ('RECEIVED','READY','APPLIED','WAITING_LOCAL','WAITING_DEPENDENCY','REQUIRES_REVIEW'))
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO sync_inbox_b10_new (scope_id,organization_id,server_revision,aggregate_type,aggregate_id,operation_type,entity_version,payload_version,payload_json,origin_mutation_id,transaction_id,transaction_order,transaction_size,deleted_at,changed_at,content_fingerprint,apply_state,apply_error_code,received_at,applied_at) SELECT scope_id,organization_id,server_revision,aggregate_type,aggregate_id,operation_type,entity_version,payload_version,payload_json,origin_mutation_id,transaction_id,transaction_order,transaction_size,deleted_at,changed_at,content_fingerprint,apply_state,apply_error_code,received_at,applied_at FROM sync_inbox")
        db.execSQL("DROP TABLE sync_inbox")
        db.execSQL("ALTER TABLE sync_inbox_b10_new RENAME TO sync_inbox")
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
        db.execSQL("ALTER TABLE sync_inbox_group ADD COLUMN serialized_bytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_inbox_apply_request (
            scope_id TEXT NOT NULL, organization_id TEXT NOT NULL,
            requested_generation INTEGER NOT NULL, drained_generation INTEGER NOT NULL,
            next_wake_at INTEGER, storage_wait_reason TEXT, updated_at INTEGER NOT NULL,
            PRIMARY KEY(scope_id), CHECK(requested_generation>=drained_generation),
            CHECK(drained_generation>=0), CHECK(next_wake_at IS NULL OR next_wake_at>=0)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_inbox_apply_request_wake ON sync_inbox_apply_request (organization_id,next_wake_at)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_inbox_dependency (
            scope_id TEXT NOT NULL, transaction_id TEXT NOT NULL, depends_on_transaction_id TEXT NOT NULL,
            PRIMARY KEY(scope_id,transaction_id,depends_on_transaction_id)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_inbox_dependency_target ON sync_inbox_dependency (scope_id,depends_on_transaction_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_inbox_touched_key (
            scope_id TEXT NOT NULL, transaction_id TEXT NOT NULL, key_type TEXT NOT NULL, key_id TEXT NOT NULL,
            PRIMARY KEY(scope_id,transaction_id,key_type,key_id)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_inbox_touched_key_lookup ON sync_inbox_touched_key (scope_id,key_type,key_id)")
        db.execSQL("ALTER TABLE sync_inbox_group ADD COLUMN last_attempt_generation INTEGER NOT NULL DEFAULT -1")
        // Before B10 page receipt and apply were atomic; page_high_watermark was a SERVER
        // observation, not proof of received coverage. Preserve tokens and real applied anchors.
        db.execSQL("""UPDATE sync_cursor SET received_high_watermark=COALESCE(applied_checkpoint,last_applied_change_revision,0)
            WHERE NOT EXISTS(SELECT 1 FROM sync_inbox i WHERE i.scope_id=sync_cursor.scope_id AND i.apply_state<>'APPLIED')
            AND NOT EXISTS(SELECT 1 FROM sync_inbox_group g WHERE g.scope_id=sync_cursor.scope_id AND g.state<>'APPLIED')""")
        // Old receipts do not prove B10 canonical group bytes or dependency coverage.
        db.execSQL("""UPDATE sync_inbox_group SET state='REQUIRES_REVIEW',
            wait_reason='INBOX_LEGACY_MANIFEST_UNPROVEN', applied_at=NULL WHERE state<>'APPLIED'""")
        db.execSQL("""INSERT OR IGNORE INTO sync_inbox_apply_request
            (scope_id,organization_id,requested_generation,drained_generation,next_wake_at,storage_wait_reason,updated_at)
            SELECT scope_id,organization_id,1,0,0,'INBOX_LEGACY_MANIFEST_UNPROVEN',0
            FROM sync_inbox WHERE apply_state<>'APPLIED' GROUP BY scope_id,organization_id""")
    }
}
