package com.verto.app.data.sync.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LegacySyncMigrationPlannerTest {
    private fun candidate(
        state: String = "PENDING",
        targetMutationId: String? = "m1",
        disposition: String = LegacySyncMigrationDisposition.MIGRATED,
        reason: String? = null,
    ) = LegacySyncMigrationCandidate(
        sourceKind = "FINANCIAL_OUTBOX",
        sourceId = "event-1",
        aggregateType = "INVOICE",
        aggregateId = "invoice-1",
        sourceState = state,
        businessIdentity = "INVOICE_POSTED:write-1",
        sourceSequence = 7,
        targetKind = LegacySyncMigrationTarget.STRONGER_SOURCE,
        targetMutationId = targetMutationId,
        disposition = disposition,
        reasonCode = reason,
        createdAt = 100,
    )

    @Test
    fun `source fingerprint is stable when delivery fate changes`() {
        val pending = candidate()
        val acked = candidate(
            state = "ACKNOWLEDGED",
            disposition = LegacySyncMigrationDisposition.RECEIPT_CONFIRMED,
        )
        assertEquals(pending.fingerprint, acked.fingerprint)
    }

    @Test
    fun `source fingerprint is stable when durable tenant proof is discovered later`() {
        val unresolved = candidate(
            targetMutationId = null,
            disposition = LegacySyncMigrationDisposition.REQUIRES_REVIEW,
            reason = "M03_ORG_SCOPE_UNPROVEN",
        ).copy(
            sourceKind = "DIRTY_INVENTORY_ITEM_UNSCOPED",
            sourceId = "item-1",
            aggregateType = "INVENTORY_ITEM",
            aggregateId = "item-1",
            businessIdentity = "item-1",
            targetKind = LegacySyncMigrationTarget.NONE,
        )
        val proven = unresolved.copy(
            targetKind = LegacySyncMigrationTarget.UNIFIED_OUTBOX,
            targetMutationId = "mutation-item-1",
            disposition = LegacySyncMigrationDisposition.MIGRATED,
            reasonCode = null,
        )
        assertEquals(unresolved.fingerprint, proven.fingerprint)
    }

    @Test
    fun `source fingerprint changes when immutable business identity changes`() {
        val original = candidate()
        val changed = original.copy(businessIdentity = "INVOICE_POSTED:write-2")
        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    @Test
    fun `source digest is order independent`() {
        val first = candidate()
        val second = candidate().copy(sourceId = "event-2", aggregateId = "invoice-2")
        assertEquals(
            LegacySyncMigrationPlanner.sourceDigest(listOf(first, second)),
            LegacySyncMigrationPlanner.sourceDigest(listOf(second, first)),
        )
    }

    @Test
    fun `delivery states map to one documented fate`() {
        assertEquals(LegacySyncMigrationDisposition.MIGRATED, LegacySyncMigrationPlanner.dispositionForState("PENDING"))
        assertEquals(LegacySyncMigrationDisposition.RECEIPT_CONFIRMED, LegacySyncMigrationPlanner.dispositionForState("ACKNOWLEDGED"))
        assertEquals(LegacySyncMigrationDisposition.RECEIPT_CONFIRMED, LegacySyncMigrationPlanner.dispositionForState("SYNCED"))
        assertEquals(LegacySyncMigrationDisposition.REQUIRES_REVIEW, LegacySyncMigrationPlanner.dispositionForState("REJECTED"))
        assertEquals(LegacySyncMigrationDisposition.REQUIRES_REVIEW, LegacySyncMigrationPlanner.dispositionForState("BLOCKED"))
    }
}
