package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.SyncWriteBatchEntity
import com.verto.app.data.local.entity.SyncWriteBatchMemberEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncRepairV2DaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun observedVersionNeverAdvancesAppliedVersion() = runBlocking {
        val dao = database.unifiedSyncDao()
        val unknown = dao.recordObservedVersion("org", "scope", "invoice", "invoice-1", null, 1)
        assertNull(unknown.observedServerVersion)
        assertNull(unknown.appliedServerVersion)

        val observed = dao.recordObservedVersion("org", "scope", "invoice", "invoice-1", 7, 2)
        assertEquals(7L, observed.observedServerVersion)
        assertNull(observed.appliedServerVersion)

        val applied = dao.recordAppliedVersion(
            "org", "scope", "invoice", "invoice-1", 5, 41, HASH_A, false, 3,
        )
        assertEquals(7L, applied.observedServerVersion)
        assertEquals(5L, applied.appliedServerVersion)

        try {
            dao.recordAppliedVersion(
                "org", "scope", "invoice", "invoice-1", 4, 42, HASH_B, false, 4,
            )
            fail("a stale applied version must be rejected")
        } catch (_: IllegalStateException) {
            // Expected: applied authority is monotonic and is not inferred from observation time.
        }
    }

    @Test
    fun localGenerationUsesStoredSequenceNotWallClock() = runBlocking {
        val dao = database.unifiedSyncDao()
        val first = dao.recordLocalGeneration("org", "invoice", "invoice-1", HASH_A, 9_000)
        val second = dao.recordLocalGeneration("org", "invoice", "invoice-1", HASH_B, 1)

        assertEquals(1L, first.generation)
        assertEquals(2L, second.generation)
        assertEquals(1L, second.updatedAt)
        assertEquals(HASH_B, second.contentHash)
    }

    @Test
    fun sealedBatchPersistsOnlyACompleteZeroBasedManifest() = runBlocking {
        val dao = database.unifiedSyncDao()
        val batch = SyncWriteBatchEntity(
            organizationId = "org",
            batchId = "batch-1",
            memberCount = 2,
            manifestSha256 = HASH_A,
            wireJson = null,
            wireSha256 = null,
            preparedAt = null,
            sealedAt = 10,
            createdAt = 9,
        )
        dao.insertSealedWriteBatch(batch, listOf(member(0, "mutation-1"), member(1, "mutation-2")))

        try {
            dao.insertSealedWriteBatch(
                batch.copy(batchId = "batch-2"),
                listOf(member(0, "mutation-3", "batch-2"), member(2, "mutation-4", "batch-2")),
            )
            fail("a non-contiguous manifest must be rejected before persistence")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun member(order: Int, mutationId: String, batchId: String = "batch-1") =
        SyncWriteBatchMemberEntity(
            organizationId = "org",
            batchId = batchId,
            memberOrder = order,
            mutationId = mutationId,
            sourceOwner = "owner-$order-$batchId",
            sourceId = "source-$order-$batchId",
        )

    private companion object {
        val HASH_A = "a".repeat(64)
        val HASH_B = "b".repeat(64)
    }
}
