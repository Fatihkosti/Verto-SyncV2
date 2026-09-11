package com.verto.app.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration97To98FrozenLeaseInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sync-repair-97-98.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun cleanup() { context.deleteDatabase(dbName) }

    @Test
    fun pendingOwnerSurvivesAndReceivesNullableScopeEpoch() {
        helper.createDatabase(dbName, 97).apply {
            execSQL(
                """INSERT INTO sync_outbox(
                    mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,
                    base_version,local_sequence,aggregate_sequence,payload_version,payload_json,
                    semantic_fingerprint,state,attempt_count,next_attempt_at,created_at
                ) VALUES('m1','org','NOTE','n1','UPSERT',NULL,1,1,1,'{}','fp','PENDING',0,0,1)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 98, true, MIGRATION_97_98)
        db.query("SELECT state,lease_scope_epoch FROM sync_outbox WHERE mutation_id='m1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("PENDING", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.close()
    }
}
