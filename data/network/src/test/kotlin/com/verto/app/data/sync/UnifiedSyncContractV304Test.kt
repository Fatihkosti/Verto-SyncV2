package com.verto.app.data.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnifiedSyncContractV304Test {
    private val scope = SyncScope(
        organizationId = "org-1",
        syncPrincipalId = "user-1",
        scopeId = "global",
        scopeDefinitionVersion = 1,
    )

    @Test fun `contract identity is stable and versioned`() {
        assertEquals("verto-unified-sync", UNIFIED_SYNC_CONTRACT_FAMILY)
        assertEquals(SYNC_REPAIR_CONTRACT_VERSION, UNIFIED_SYNC_CONTRACT_VERSION)
        assertEquals(2, UNIFIED_SYNC_CONTRACT_VERSION)
    }

    @Test fun `mutation serialization round trip preserves required identity`() {
        val mutation = mutation("m-1")
        val encoded = Json.encodeToString(mutation)
        assertEquals(mutation, Json.decodeFromString<SyncMutation>(encoded))
    }

    @Test fun `missing required mutation field fails closed`() {
        expectThrows<SerializationException> {
            Json.decodeFromString<SyncMutation>(
                """{"organizationId":"org-1","aggregateType":"NOTE","aggregateId":"n-1","operationType":"UPSERT","localSequence":1,"aggregateSequence":1,"payloadVersion":1,"createdAtEpochMillis":1}"""
            )
        }
    }

    @Test fun `unknown protocol error enum fails closed`() {
        expectThrows<SerializationException> { Json.decodeFromString<SyncProtocolErrorType>("\"FUTURE_ERROR\"") }
    }

    @Test fun `all required protocol error categories serialize`() {
        val expected = listOf("TRANSIENT", "AUTH", "VALIDATION", "CONFLICT", "CURSOR_EXPIRED", "CONTRACT_UNSUPPORTED")
        assertEquals(expected, SyncProtocolErrorType.entries.map { it.name })
    }

    @Test fun `retry preserves stable mutation identity`() {
        val original = mutation("m-stable")
        UnifiedSyncContractRules.requireStableRetryIdentity(original, original.copy(createdAtEpochMillis = 999L))
    }

    @Test fun `same mutation id with changed semantic content fails closed`() {
        val original = mutation("m-conflict")
        val changed = original.copy(payload = JsonObject(mapOf("name" to JsonPrimitive("changed"))))
        expectViolation("IDEMPOTENCY_CONFLICT") {
            UnifiedSyncContractRules.requireStableRetryIdentity(original, changed)
        }
    }

    @Test fun `revision gaps are valid and do not imply loss`() {
        UnifiedSyncContractRules.requireRevisionSequence(
            listOf(change(10, "tx-10"), change(11, "tx-11"), change(15, "tx-15"), change(22, "tx-22"))
        )
    }

    @Test fun `revision regression fails closed`() {
        expectViolation("INVALID_REVISION_ORDER") {
            UnifiedSyncContractRules.requireRevisionSequence(listOf(change(22, "tx-22"), change(15, "tx-15")))
        }
    }

    @Test fun `duplicate revision with identical content is replay safe`() {
        val c = change(25, "tx-25")
        UnifiedSyncContractRules.requireRevisionSequence(listOf(c, c))
    }

    @Test fun `duplicate revision with divergent content fails closed`() {
        val c = change(25, "tx-25")
        expectViolation("DUPLICATE_REVISION_CONTENT_MISMATCH") {
            UnifiedSyncContractRules.requireRevisionSequence(listOf(c, c.copy(aggregateId = "other")))
        }
    }

    @Test fun `complete server transaction group is accepted atomically`() {
        val group = listOf(
            change(30, "tx-atomic", transactionOrder = 0, transactionSize = 2),
            change(35, "tx-atomic", transactionOrder = 1, transactionSize = 2),
        )
        UnifiedSyncContractRules.requireCompleteTransactionGroups(group)
    }

    @Test fun `split server transaction group fails closed`() {
        expectViolation("INCOMPLETE_TRANSACTION_GROUP") {
            UnifiedSyncContractRules.requireCompleteTransactionGroups(
                listOf(change(30, "tx-split", transactionOrder = 0, transactionSize = 2))
            )
        }
    }

    @Test fun `filtered pull can never advance global cursor`() {
        val page = SyncPullPage(
            changes = emptyList(),
            nextCursor = "opaque:42",
            hasMore = false,
            endsAtTransactionBoundary = true,
            scopeIdentity = scope,
            coverage = SyncPullCoverage.FILTERED_SCOPE,
            advancesGlobalCursor = true,
        )
        expectViolation("FILTERED_GLOBAL_CURSOR_ADVANCE") {
            UnifiedSyncContractRules.requireValidPullPage(page, scope)
        }
    }

    @Test fun `pull page is bound to principal scope and contract`() {
        val wrongScope = scope.copy(syncPrincipalId = "user-2")
        val page = SyncPullPage(
            changes = emptyList(),
            nextCursor = "opaque:43",
            hasMore = false,
            endsAtTransactionBoundary = true,
            scopeIdentity = wrongScope,
        )
        expectViolation("SCOPE_MISMATCH") { UnifiedSyncContractRules.requireValidPullPage(page, scope) }
    }

    @Test fun `bootstrap requires bound nonblank baseline cursor`() {
        val valid = BootstrapSession(
            bootstrapSessionId = "bs-1",
            scope = scope,
            baselineCursor = "opaque:baseline",
            snapshotComplete = false,
            expiresAtEpochMillis = 2_000L,
        )
        UnifiedSyncContractRules.requireValidBootstrap(valid)
        expectViolation("VALIDATION") {
            UnifiedSyncContractRules.requireValidBootstrap(valid.copy(baselineCursor = ""))
        }
    }

    @Test fun `command dependency must precede dependent mutation`() {
        val parent = mutation("m-parent").copy(commandBatchId = "b-1", commandOrder = 0)
        val child = mutation("m-child").copy(commandBatchId = "b-1", commandOrder = 1, dependsOnMutationId = "m-parent")
        UnifiedSyncContractRules.requireValidCommandBatch(
            CommandBatch("b-1", atomic = true, orderedMutationIds = listOf("m-parent", "m-child")),
            listOf(parent, child),
        )
        expectViolation("DEPENDENCY_ORDER_VIOLATION") {
            UnifiedSyncContractRules.requireValidCommandBatch(
                CommandBatch("b-1", atomic = true, orderedMutationIds = listOf("m-child", "m-parent")),
                listOf(parent, child),
            )
        }
    }

    @Test fun `unknown aggregate and unsupported payload version fail closed`() {
        expectViolation("CONTRACT_UNSUPPORTED") {
            UnifiedSyncContractRules.requireKnownAggregate("UNKNOWN_AGGREGATE", 1)
        }
        expectViolation("CONTRACT_UNSUPPORTED") {
            UnifiedSyncContractRules.requireKnownAggregate("PARTY_IDENTITY", 1)
        }
    }

    @Test fun `realtime hint is informational and does not define cursor`() {
        UnifiedSyncContractRules.requireRealtimeHintOnly(
            SyncRealtimeHint("org-1", aggregateType = "NOTE", aggregateId = "n-1", serverRevision = 100L)
        )
        expectViolation("VALIDATION") {
            UnifiedSyncContractRules.requireRealtimeHintOnly(SyncRealtimeHint("org-1", serverRevision = 0L))
        }
    }

    @Test fun `reconciliation manifest requires known aggregate and deterministic metadata`() {
        UnifiedSyncContractRules.requireValidManifest(
            SyncReconciliationManifest("NOTE", "00", 10, "sha256:abc", 44, "global")
        )
    }

    private fun mutation(id: String) = SyncMutation(
        mutationId = id,
        organizationId = "org-1",
        aggregateType = "NOTE",
        aggregateId = "n-1",
        operationType = SyncMutationOperation.UPSERT,
        localSequence = 1,
        aggregateSequence = 1,
        payloadVersion = 1,
        payload = JsonObject(mapOf("name" to JsonPrimitive("stable"))),
        createdAtEpochMillis = 1L,
    )

    private fun change(
        revision: Long,
        transactionId: String,
        transactionOrder: Int = 0,
        transactionSize: Int = 1,
    ) = SyncChange(
        revision = revision,
        organizationId = "org-1",
        syncScopeId = "global",
        aggregateType = "NOTE",
        aggregateId = "n-$revision",
        operationType = SyncMutationOperation.UPSERT,
        payloadVersion = 1,
        transactionId = transactionId,
        transactionOrder = transactionOrder,
        transactionSize = transactionSize,
        changedAtEpochMillis = 1L,
    )

    private fun expectViolation(code: String, block: () -> Unit) {
        try {
            block()
            fail("Expected SyncContractViolation($code)")
        } catch (e: SyncContractViolation) {
            assertEquals(code, e.code)
        }
    }

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            assertTrue("Expected ${T::class.simpleName}, got ${e::class.simpleName}", e is T)
        }
    }
}
