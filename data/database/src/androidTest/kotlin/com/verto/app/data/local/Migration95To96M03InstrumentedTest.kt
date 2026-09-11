package com.verto.app.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration95To96M03InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "m03-migration-95-96.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun pendingIntentSurvives95To96AndJournalStartsEmpty() {
        val mutationId = "m03-pending-mutation"
        val dependencyId = "m03-parent-mutation"
        helper.createDatabase(dbName, 95).apply {
            execSQL(
                """
                INSERT INTO sync_outbox(
                    mutation_id, organization_id, aggregate_type, aggregate_id, operation_type,
                    base_version, local_sequence, aggregate_sequence, payload_version, payload_json,
                    semantic_fingerprint, command_batch_id, command_order, depends_on_mutation_id,
                    state, attempt_count, last_error_type, last_error_code, next_attempt_at,
                    lease_owner, lease_token, lease_expires_at, acked_server_revision,
                    acked_server_version, receipt_status, created_at, acked_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
                arrayOf<Any?>(
                    mutationId, "org-m03", "CATEGORY", "category-1", "UPSERT",
                    null, 10L, 1L, 1, "{}", "fp-category-1",
                    "batch-1", 2, dependencyId,
                    "PENDING", 0, null, null, 0L,
                    null, null, null, null, null, null, 100L, null,
                )
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 96, true, MIGRATION_95_96)
        migrated.query("SELECT mutation_id, depends_on_mutation_id, state FROM sync_outbox WHERE mutation_id=?", arrayOf(mutationId)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(mutationId, c.getString(0))
            assertEquals(dependencyId, c.getString(1))
            assertEquals("PENDING", c.getString(2))
            assertFalse(c.moveToNext())
        }
        migrated.query("SELECT COUNT(*) FROM sync_legacy_migration_entry").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0))
        }
        migrated.query("SELECT COUNT(*) FROM sync_legacy_migration_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0))
        }
        migrated.close()
    }
}
