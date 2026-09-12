package com.verto.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrozenMutationStoreInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var store: FrozenMutationStore
    private lateinit var writer: UnifiedOutboxWriter
    private lateinit var batches: SyncBatchCoordinatorV2

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val protection = SyncPendingProtection(database)
        store = FrozenMutationStore(database, protection)
        writer = UnifiedOutboxWriter(database, store)
        batches = SyncBatchCoordinatorV2(database, store)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun transactionRollbackRemovesDomainIntentGenerationAndReferences() = runBlocking {
        runCatching {
            database.withTransaction {
                writer.enqueue("org", "EXPENSE", "expense-1", "UPSERT", mapOf("note" to "draft"), mutationId = "m-rollback")
                error("force rollback")
            }
        }

        val dao = database.unifiedSyncDao()
        assertNull(dao.getOutbox("m-rollback"))
        assertNull(dao.readMutationPacket("org", "m-rollback"))
        assertNull(dao.readLocalGeneration("org", "EXPENSE", "expense-1"))
        assertTrue(dao.listPendingReferences("org", "EXPENSE", "expense-1").isEmpty())
    }

    @Test
    fun captureRecordsGenerationAndReferenceForEveryProtectedKey() = runBlocking {
        val root = ProtectedSyncKey("INVOICE", "invoice-1")
        val line = ProtectedSyncKey("INVOICE_LINE", "line-1")
        database.withTransaction {
            writer.enqueue(
                organizationId = "org",
                aggregateType = "INVOICE",
                aggregateId = "invoice-1",
                operationType = "UPSERT",
                payload = mapOf("status" to "DRAFT"),
                mutationId = "m-protected",
                protectedContentHashes = mapOf(
                    root to sha256Utf8("invoice-content"),
                    line to sha256Utf8("line-content"),
                ),
                createdAt = 10,
            )
        }

        val dao = database.unifiedSyncDao()
        assertEquals(1L, dao.readLocalGeneration("org", root.type, root.id)!!.generation)
        assertEquals(1L, dao.readLocalGeneration("org", line.type, line.id)!!.generation)
        assertEquals(1, dao.listPendingReferences("org", root.type, root.id).size)
        assertEquals(1, dao.listPendingReferences("org", line.type, line.id).size)
    }

    @Test
    fun predecessorUsesReceiptVersionWhileEachOfflineEditKeepsItsOwnBytes() = runBlocking {
        database.withTransaction {
            writer.enqueue("org", "EXPENSE", "expense-1", "UPSERT", mapOf("note" to "first"), mutationId = "m-1", baseVersion = 5, createdAt = 10)
            writer.enqueue("org", "EXPENSE", "expense-1", "UPSERT", mapOf("note" to "second"), mutationId = "m-2", createdAt = 11)
        }
        val dao = database.unifiedSyncDao()
        val first = store.prepareOnce("org", "m-1", 20)
        assertEquals(5L, first.baseVersion)
        assertEquals("m-1", dao.readMutationPacket("org", "m-2")!!.predecessorMutationId)
        assertTrue(runCatching { store.prepareOnce("org", "m-2", 21) }.exceptionOrNull()?.message == "PREDECESSOR_NOT_ACKNOWLEDGED")

        val firstOutbox = dao.getOutbox("m-1")!!
        assertEquals(1, dao.tryLease("m-1", "PENDING", "test", "lease-1", 7, 200))
        assertEquals(
            FrozenAckOutcome.ACKNOWLEDGED_LOCAL_CHANGED,
            store.acknowledgeUnified(
                "org", "m-1", "lease-1", 7, firstOutbox.semanticFingerprint,
                "m-1", first.wireSha256, 40, 6, "APPLIED", 30,
            ),
        )
        val second = store.prepareOnce("org", "m-2", 31)
        assertEquals(6L, second.baseVersion)
        assertNotEquals(first.wireJson, second.wireJson)
        assertEquals(first, store.prepareOnce("org", "m-1", 999))
        assertEquals(second, store.prepareOnce("org", "m-2", 999))
        assertTrue(dao.listPendingReferences("org", "EXPENSE", "expense-1").all { it.sourceId == "m-2" })
    }

    @Test
    fun staleLeaseOrWrongReceiptCannotAcknowledgeCurrentOwner() = runBlocking {
        database.withTransaction {
            writer.enqueue("org", "NOTE", "note-1", "UPSERT", mapOf("text" to "safe"), mutationId = "m-stale", createdAt = 10)
        }
        val dao = database.unifiedSyncDao()
        val frozen = store.prepareOnce("org", "m-stale", 11)
        val outbox = dao.getOutbox("m-stale")!!
        assertEquals(1, dao.tryLease("m-stale", "PENDING", "old", "old-token", 1, 100))
        assertEquals(1, dao.markRetry("m-stale", "old-token", 1, "TRANSIENT_NETWORK", "timeout", 0))
        assertEquals(1, dao.tryLease("m-stale", "RETRY", "new", "new-token", 2, 300))

        assertEquals(
            FrozenAckOutcome.STALE_LEASE,
            store.acknowledgeUnified("org", "m-stale", "old-token", 1, outbox.semanticFingerprint, "m-stale", frozen.wireSha256, 1, 1, "APPLIED", 20),
        )
        assertEquals(
            FrozenAckOutcome.RECEIPT_MISMATCH,
            store.acknowledgeUnified("org", "m-stale", "new-token", 2, outbox.semanticFingerprint, "m-stale", "f".repeat(64), 1, 1, "APPLIED", 21),
        )
        assertEquals("LEASED", dao.getOutbox("m-stale")!!.state)
        assertEquals("new-token", dao.getOutbox("m-stale")!!.leaseToken)
    }

    @Test
    fun sealedBatchFreezesMemberTextWithoutMovingMembersOutOfOwnerQueue() = runBlocking {
        database.withTransaction {
            writer.enqueue("org", "NOTE", "note-1", "UPSERT", mapOf("text" to "a"), mutationId = "bm-1", commandBatchId = "batch-1", commandOrder = 0, createdAt = 10)
            writer.enqueue("org", "REMINDER", "reminder-1", "UPSERT", mapOf("title" to "b"), mutationId = "bm-2", commandBatchId = "batch-1", commandOrder = 1, createdAt = 11)
            batches.seal("org", "batch-1", listOf("bm-1", "bm-2"), 12)
        }
        val first = batches.prepareOnce("org", "batch-1", 13)
        val retry = batches.prepareOnce("org", "batch-1", 99)

        assertEquals(first, retry)
        assertTrue(first.wireJson.contains("\\\"mutationId\\\":\\\"bm-1\\\""))
        assertTrue(first.wireJson.contains("\\\"mutationId\\\":\\\"bm-2\\\""))
        assertEquals("PENDING", database.unifiedSyncDao().getOutbox("bm-1")!!.state)
        assertEquals("PENDING", database.unifiedSyncDao().getOutbox("bm-2")!!.state)
    }

    @Test
    fun sealedBatchAcknowledgementIsAtomicAndReplaySafe() = runBlocking {
        database.withTransaction {
            writer.enqueue("org", "NOTE", "note-1", "UPSERT", mapOf("text" to "a"), mutationId = "ack-1", commandBatchId = "batch-ack", commandOrder = 0, createdAt = 10)
            writer.enqueue("org", "REMINDER", "reminder-1", "UPSERT", mapOf("title" to "b"), mutationId = "ack-2", commandBatchId = "batch-ack", commandOrder = 1, createdAt = 11)
            batches.seal("org", "batch-ack", listOf("ack-1", "ack-2"), 12)
        }
        batches.prepareOnce("org", "batch-ack", 13)
        val dao = database.unifiedSyncDao()
        val members = dao.listWriteBatchMembers("org", "batch-ack")
        store.recordBatchDispatch("org", "batch-ack", members, 14)
        val firstHash = dao.readMutationPacket("org", "ack-1")!!.wireSha256!!
        val secondHash = dao.readMutationPacket("org", "ack-2")!!.wireSha256!!
        assertEquals(14L, dao.readMutationPacket("org", "ack-1")!!.firstDispatchAt)

        val mismatched = listOf(
            SyncReceipt(SyncReceiptStatus.APPLIED, "ack-1", "note-1", serverVersion = 1, serverRevision = 20, requestHash = firstHash),
            SyncReceipt(SyncReceiptStatus.APPLIED, "ack-2", "reminder-1", serverVersion = 1, serverRevision = 21, requestHash = "f".repeat(64)),
        )
        assertTrue(runCatching { store.acknowledgeSealedBatch("org", "batch-ack", members, mismatched, 15) }.isFailure)
        assertEquals("PENDING", dao.getOutbox("ack-1")!!.state)
        assertEquals("PENDING", dao.getOutbox("ack-2")!!.state)

        val accepted = listOf(
            SyncReceipt(SyncReceiptStatus.APPLIED, "ack-1", "note-1", serverVersion = 1, serverRevision = 20, requestHash = firstHash),
            SyncReceipt(SyncReceiptStatus.APPLIED, "ack-2", "reminder-1", serverVersion = 1, serverRevision = 21, requestHash = secondHash),
        )
        assertEquals(2, store.acknowledgeSealedBatch("org", "batch-ack", members, accepted, 16))
        assertEquals("ACKNOWLEDGED", dao.getOutbox("ack-1")!!.state)
        assertEquals("ACKNOWLEDGED", dao.getOutbox("ack-2")!!.state)
        assertEquals(2, store.acknowledgeSealedBatch("org", "batch-ack", members, accepted, 17))
    }
}
