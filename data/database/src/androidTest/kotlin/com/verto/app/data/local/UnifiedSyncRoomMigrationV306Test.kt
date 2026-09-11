package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Future executable regression artifact; Session 306 acceptance is static-only. */
@RunWith(AndroidJUnit4::class)
class UnifiedSyncRoomMigrationV306Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun schema_77_to_78_preserves_specialized_state_and_starts_unified_state_empty() {
        val name = "unified-sync-v306-migration"
        helper.createDatabase(name, 77).apply {
            execSQL("INSERT INTO party_sync_outbox VALUES('p','op','PARTY','a',0,2,'{}','PENDING',0,'',0,1)")
            execSQL("INSERT INTO financial_outbox(event_id,organization_id,write_id,aggregate_id,aggregate_version,sequence,operation_type,payload,payload_version,schema_version,occurred_at,recorded_at,created_at) VALUES('f','org','w','a',1,1,'UPSERT','{}',1,1,1,1,1)")
            execSQL("INSERT INTO financial_inbox(event_id,organization_id,aggregate_id,aggregate_version,sequence,operation_type,payload_version,schema_version,payload,occurred_at,recorded_at,server_revision,apply_state,received_at) VALUES('fi','org','a',1,1,'UPSERT',1,1,'{}',1,1,1,'APPLIED',1)")
            execSQL("INSERT INTO inventory_stock_outbox(id,organization_id,command_id,idempotency_key,movement_id,item_id,operation,signed_base_quantity,created_at) VALUES('i','org','c','k','m','item','UPSERT',1,1)")
            execSQL("INSERT INTO inventory_sync_cursors VALUES('org',7,3,1)")
            execSQL("INSERT INTO optimal_outbox(organization_id,event_id,aggregate_type,aggregate_id,operation,payload_json,payload_version,idempotency_key,sequence,status,attempt_count,created_at,updated_at) VALUES('org','o','X','a','UPSERT','{}',1,'k',1,'PENDING',0,1,1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 78, true, MIGRATION_77_78)
        listOf("party_sync_outbox", "financial_outbox", "financial_inbox", "inventory_stock_outbox", "inventory_sync_cursors", "optimal_outbox").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); assertEquals(1L, c.getLong(0)) }
        }
        listOf("sync_outbox", "sync_inbox", "sync_cursor", "sync_sequence_state").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
        }
        db.close()
    }
}
