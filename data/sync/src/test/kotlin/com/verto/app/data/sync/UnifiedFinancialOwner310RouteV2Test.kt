package com.verto.app.data.sync

import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.sync.push.UnifiedFinancialOwner310Route
import com.verto.app.data.sync.push.UnifiedSyncPushFailure
import com.verto.app.data.sync.push.UnifiedSyncPushRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnifiedFinancialOwner310RouteV2Test {
    private val registry = UnifiedSyncPushRegistry()

    @Test fun `prepare returns only the frozen owner310 payload`() = runTest {
        val first = row("{\"materialization\":{\"id\":\"credit-1\",\"amountMinor\":100}}")
        val second = row("{\"materialization\":{\"id\":\"credit-1\",\"amountMinor\":200}}")

        assertEquals(100L, UnifiedFinancialOwner310Route.prepare(first, registry)
            .payload["materialization"]!!.jsonObject["amountMinor"]!!.jsonPrimitive.long)
        assertEquals(200L, UnifiedFinancialOwner310Route.prepare(second, registry)
            .payload["materialization"]!!.jsonObject["amountMinor"]!!.jsonPrimitive.long)
    }

    @Test fun `prepare rejects missing frozen materialization`() {
        assertThrows(UnifiedSyncPushFailure::class.java) {
            kotlinx.coroutines.runBlocking { UnifiedFinancialOwner310Route.prepare(row("{}"), registry) }
        }
    }

    private fun row(payload: String) = SyncOutboxEntity(
        mutationId = "mutation-1", organizationId = "org-1", aggregateType = "CLIENT_CREDIT",
        aggregateId = "credit-1", operationType = "COMMAND", baseVersion = null,
        localSequence = 1, aggregateSequence = 1, payloadVersion = 2, payloadJson = payload,
        semanticFingerprint = "fingerprint", commandBatchId = null, commandOrder = null,
        dependsOnMutationId = null, createdAt = 10,
    )
}
