package com.verto.app.data.local

import com.verto.app.data.local.dao.PendingSourceIdentity
import com.verto.app.data.local.dao.findOrphanPendingSources
import com.verto.app.data.local.dao.validateWriteBatchManifest
import com.verto.app.data.local.entity.SyncWriteBatchEntity
import com.verto.app.data.local.entity.SyncWriteBatchMemberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncRepairV2InvariantTest {
    private val hash = "a".repeat(64)
    private val batch = SyncWriteBatchEntity(
        organizationId = "org",
        batchId = "batch",
        memberCount = 2,
        manifestSha256 = hash,
        wireJson = null,
        wireSha256 = null,
        preparedAt = null,
        sealedAt = 1,
        createdAt = 1,
    )

    @Test
    fun `batch order is contiguous and zero based`() {
        validateWriteBatchManifest(batch, listOf(member(0, "m0"), member(1, "m1")))
        try {
            validateWriteBatchManifest(batch, listOf(member(0, "m0"), member(2, "m1")))
            fail("non-contiguous order must fail")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `orphan detection covers independent source owners`() {
        val financial = PendingSourceIdentity("financial_outbox", "financial-1")
        val inventory = PendingSourceIdentity("inventory_stock_outbox", "inventory-1")
        val missing = PendingSourceIdentity("optimal_outbox", "missing-1")
        assertEquals(
            setOf(missing),
            findOrphanPendingSources(
                references = listOf(financial, inventory, missing),
                sourceIdsByOwner = mapOf(
                    "financial_outbox" to setOf("financial-1"),
                    "inventory_stock_outbox" to setOf("inventory-1"),
                    "optimal_outbox" to emptySet(),
                ),
            ),
        )
    }

    private fun member(order: Int, mutationId: String) = SyncWriteBatchMemberEntity(
        organizationId = "org",
        batchId = "batch",
        memberOrder = order,
        mutationId = mutationId,
        sourceOwner = "owner-$order",
        sourceId = "source-$order",
    )
}
