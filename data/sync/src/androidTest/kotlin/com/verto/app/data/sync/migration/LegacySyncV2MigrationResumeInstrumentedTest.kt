package com.verto.app.data.sync.migration

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.ALL_MIGRATIONS
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.SyncDeletionSnapshot
import com.verto.app.data.sync.SyncWorkScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySyncV2MigrationResumeInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "m03-resume.db"
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = openDb()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun prepareIsDurableAndIdempotentAcrossDatabaseRestart() = runBlocking {
        val org = "org-m03"
        val user = "user-m03"
        val mutationId = "mutation-category-1"
        val dependencyId = "mutation-parent-1"
        seedUnifiedPending(org, mutationId, dependencyId)
        val deletions = emptyDeletions(categoryIds = setOf("category-1"), invoiceIds = setOf("invoice-needs-domain-command"))
        val scope = SyncWorkScope(org, user, 1L)

        val first = LegacySyncV2MigrationCoordinator(db).prepare(scope, deletions, now = 1_000L)
        assertEquals(2, first.sourceCount)
        assertEquals(1, first.migratedCount)
        assertEquals(1, first.reviewCount)
        assertFalse(first.legacyWritesFenced)

        val beforeRestart = db.syncLegacyMigrationDao().listEntries(org)
        assertEquals(2, beforeRestart.size)
        val migrated = beforeRestart.single { it.sourceKind == "DELETION_PREF_CATEGORY" }
        assertEquals(mutationId, migrated.targetMutationId)
        assertEquals(dependencyId, migrated.dependsOnSourceId)
        assertEquals("MIGRATED", migrated.disposition)
        val review = beforeRestart.single { it.sourceKind == "DELETION_PREF_INVOICE" }
        assertEquals("REQUIRES_REVIEW", review.disposition)
        assertEquals("M03_DELETE_REQUIRES_DOMAIN_COMMAND", review.reasonCode)

        db.close()
        db = openDb()

        val second = LegacySyncV2MigrationCoordinator(db).prepare(scope.copy(sessionEpoch = 2L), deletions, now = 2_000L)
        assertEquals(first.sourceCount, second.sourceCount)
        assertEquals(first.migratedCount, second.migratedCount)
        assertEquals(first.reviewCount, second.reviewCount)
        assertEquals(first.sourceDigest, second.sourceDigest)

        val afterRestart = db.syncLegacyMigrationDao().listEntries(org)
        assertEquals(2, afterRestart.size)
        assertEquals(beforeRestart.map { it.sourceKind to it.sourceId }.toSet(), afterRestart.map { it.sourceKind to it.sourceId }.toSet())
        assertEquals(mutationId, afterRestart.single { it.sourceKind == "DELETION_PREF_CATEGORY" }.targetMutationId)
        assertEquals(dependencyId, afterRestart.single { it.sourceKind == "DELETION_PREF_CATEGORY" }.dependsOnSourceId)
        val state = db.syncLegacyMigrationDao().getState(org, user)
        assertEquals("REQUIRES_REVIEW", state?.phase)
        assertEquals(2, state?.sourceCount)
        assertEquals(first.sourceDigest, state?.sourceDigest)
        assertFalse(state?.legacyWritesFenced ?: true)
    }

    @Test
    fun interruptedJournalTransactionRollsBackThenPrepareResumes() = runBlocking {
        val org = "org-m03-crash"
        val user = "user-m03-crash"
        val mutationId = "mutation-category-crash"
        seedUnifiedPending(org, mutationId, "mutation-parent-crash")

        var interrupted = false
        try {
            db.withTransaction {
                db.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO sync_legacy_migration_entry(
                        organization_id,source_kind,source_id,sync_principal_id,aggregate_type,aggregate_id,
                        source_state,business_identity,target_kind,disposition,source_fingerprint,created_at,updated_at
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        org, "DELETION_PREF_CATEGORY", "category-1", user, "CATEGORY", "category-1",
                        "PENDING_DELETE", "category-1", "UNIFIED_OUTBOX", "MIGRATED", "interrupted", 1L, 1L,
                    )
                )
                throw SimulatedProcessDeath()
            }
        } catch (_: SimulatedProcessDeath) {
            interrupted = true
        }
        assertTrue(interrupted)

        db.close()
        db = openDb()
        assertEquals(0, db.syncLegacyMigrationDao().countEntries(org))

        val summary = LegacySyncV2MigrationCoordinator(db).prepare(
            SyncWorkScope(org, user, 1L),
            emptyDeletions(categoryIds = setOf("category-1")),
            now = 3_000L,
        )
        assertEquals(1, summary.sourceCount)
        assertEquals(1, summary.migratedCount)
        assertEquals(0, summary.reviewCount)
        assertEquals(1, db.syncLegacyMigrationDao().countEntries(org))
        assertEquals(mutationId, db.syncLegacyMigrationDao().listEntries(org).single().targetMutationId)
    }

    private fun openDb(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    private fun seedUnifiedPending(org: String, mutationId: String, dependencyId: String) {
        db.openHelper.writableDatabase.execSQL(
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
                mutationId, org, "CATEGORY", "category-1", "UPSERT",
                null, 10L, 1L, 1, "{}", "fp-category-1",
                "batch-1", 2, dependencyId,
                "PENDING", 0, null, null, 0L,
                null, null, null, null, null, null, 100L, null,
            )
        )
    }

    private class SimulatedProcessDeath : RuntimeException()

    private fun emptyDeletions(
        categoryIds: Set<String> = emptySet(),
        invoiceIds: Set<String> = emptySet(),
    ) = SyncDeletionSnapshot(
        clientIds = emptySet(), invoiceIds = invoiceIds, inventoryIds = emptySet(), expenseIds = emptySet(),
        categoryIds = categoryIds, commissionIds = emptySet(), unitIds = emptySet(), budgetIds = emptySet(),
        reconciliationIds = emptySet(),
    )
}
