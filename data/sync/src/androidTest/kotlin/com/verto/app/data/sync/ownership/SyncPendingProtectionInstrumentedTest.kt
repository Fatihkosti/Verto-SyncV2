package com.verto.app.data.sync.ownership

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.local.entity.SyncPendingReferenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncPendingProtectionInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var protection: SyncPendingProtection

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        protection = SyncPendingProtection(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun contentReferenceProtectsChildAcrossNonTerminalStates() = runBlocking {
        val states = listOf("PENDING", "FAILED", "BLOCKED", "REQUIRES_REVIEW", "REJECTED", "LOCAL_RETAINED")
        states.forEachIndexed { index, state ->
            val sourceId = "mutation-$index"
            database.unifiedSyncDao().insertOutboxRaw(outbox(sourceId, index + 1L, state))
            database.withTransaction {
                protection.capture(
                    PendingSourceRef("org-1", SyncSourceOwner.UNIFIED, sourceId),
                    listOf(ProtectedSyncKey("INVOICE", "invoice-child-$index")),
                    index.toLong(), HASH, "INVOICE_CHILD",
                )
            }
            assertTrue(protection.isProtected("org-1", ProtectedSyncKey("INVOICE", "invoice-child-$index")))
        }
    }

    @Test
    fun terminalCleanupRequiresOwnerProof() = runBlocking {
        database.unifiedSyncDao().insertOutboxRaw(outbox("mutation-a", 1, "PENDING"))
        database.withTransaction {
            protection.capture(
                PendingSourceRef("org-1", SyncSourceOwner.UNIFIED, "mutation-a"),
                listOf(ProtectedSyncKey("INVOICE", "invoice-a")),
                1, HASH, "INVOICE_ROOT",
            )
        }
        database.openHelper.writableDatabase.execSQL(
            "UPDATE sync_outbox SET state='ACKNOWLEDGED' WHERE mutation_id='mutation-a'",
        )
        database.withTransaction {
            protection.releaseAfterTerminal(PendingSourceRef("org-1", SyncSourceOwner.UNIFIED, "mutation-a"))
        }
        assertFalse(protection.isProtected("org-1", ProtectedSyncKey("INVOICE", "invoice-a")))
    }

    @Test
    fun orphanAndUnscopedPartyRowsFailClosed() = runBlocking {
        database.unifiedSyncDao().insertPendingReferences(listOf(
            SyncPendingReferenceEntity(
                "org-1", "financial_outbox", "missing", "INVOICE", "invoice-orphan",
                1, HASH, "INVOICE_ROOT",
            ),
        ))
        assertTrue(protection.isProtected("org-1", ProtectedSyncKey("INVOICE", "invoice-orphan")))
        assertTrue(protection.findOrphans(
            "org-1", PendingSourceRef("org-1", SyncSourceOwner.FINANCIAL, "missing"),
        ))

        database.openHelper.writableDatabase.execSQL(
            """INSERT INTO party_sync_outbox(
               id,operation_id,aggregate_type,aggregate_id,base_revision,payload_version,
               payload_json,state,retry_count,last_error,next_attempt_at,created_at
               ) VALUES('party-source','party-operation','ROLE','party-1',0,2,'{}','PENDING',0,'',0,1)""",
        )
        try {
            database.withTransaction {
                protection.capture(
                    PendingSourceRef("org-1", SyncSourceOwner.PARTY_ROLE, "party-source"),
                    listOf(ProtectedSyncKey("PARTY_ROLE", "party-1:CUSTOMER")),
                    1, HASH, "PARTY_ROLE",
                )
            }
            fail("an unscoped Party row must not be adopted")
        } catch (failure: IllegalStateException) {
            assertTrue(failure.message.orEmpty().startsWith("BLOCKED_OWNER_UNPROVEN"))
        }
    }

    private fun outbox(id: String, sequence: Long, state: String) = SyncOutboxEntity(
        mutationId = id,
        organizationId = "org-1",
        aggregateType = "INVOICE",
        aggregateId = "invoice-root",
        operationType = "UPSERT",
        baseVersion = null,
        localSequence = sequence,
        aggregateSequence = sequence,
        payloadVersion = 1,
        payloadJson = "{}",
        semanticFingerprint = "fingerprint-$id",
        commandBatchId = null,
        commandOrder = null,
        dependsOnMutationId = null,
        state = state,
        createdAt = 1,
    )

    private companion object {
        val HASH = "a".repeat(64)
    }
}
