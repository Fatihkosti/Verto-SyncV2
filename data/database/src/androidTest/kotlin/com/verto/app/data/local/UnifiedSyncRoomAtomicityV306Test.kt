package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Future executable rollback/immutability artifact; no runtime PASS is claimed by Session 306. */
@RunWith(AndroidJUnit4::class)
class UnifiedSyncRoomAtomicityV306Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun sequence_and_outbox_rollback_together_and_semantic_update_is_rejected() {
        val name = "unified-sync-v306-atomicity"
        helper.createDatabase(name, 77).close()
        val db = helper.runMigrationsAndValidate(name, 78, true, MIGRATION_77_78)
        db.beginTransaction()
        try {
            db.execSQL("INSERT INTO sync_sequence_state VALUES('org','GLOBAL','','',1,1)")
            db.execSQL("INSERT INTO sync_outbox(mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,state,attempt_count,next_attempt_at,created_at) VALUES('m','org','X','a','UPSERT',1,1,1,'{}','fp','PENDING',0,0,1)")
            // No setTransactionSuccessful(): both writes must roll back.
        } finally {
            db.endTransaction()
        }
        db.query("SELECT COUNT(*) FROM sync_sequence_state").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }
        db.query("SELECT COUNT(*) FROM sync_outbox").use { c -> c.moveToFirst(); assertEquals(0L, c.getLong(0)) }

        db.execSQL("INSERT INTO sync_outbox(mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,state,attempt_count,next_attempt_at,created_at) VALUES('m','org','X','a','UPSERT',1,1,1,'{}','fp','PENDING',0,0,1)")
        var rejected = false
        try { db.execSQL("UPDATE sync_outbox SET payload_json='different' WHERE mutation_id='m'") } catch (_: Throwable) { rejected = true }
        assertTrue(rejected)
        db.close()
    }
}
