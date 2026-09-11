package com.verto.app.data.sync.ownership

import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncOwnershipRegistryTest {
    @Test
    fun `all 35 aggregate types have one explicit owner`() {
        assertEquals(35, SyncOwnershipRegistry.aggregates.size)
        assertEquals(UnifiedSyncAggregateRegistry.byId.keys, SyncOwnershipRegistry.byAggregateType.keys)
        assertEquals(SyncSourceOwner.FINANCIAL, SyncOwnershipRegistry.byAggregateType.getValue("PAYMENT").sourceOwner)
        assertEquals(SyncSourceOwner.PARTY_ROLE, SyncOwnershipRegistry.byAggregateType.getValue("PARTY_ROLE").sourceOwner)
        assertEquals(SyncSourceOwner.INVENTORY_STOCK, SyncOwnershipRegistry.byAggregateType.getValue("INVENTORY_MOVEMENT").sourceOwner)
        assertEquals(SyncSourceOwner.INVENTORY_COST, SyncOwnershipRegistry.byAggregateType.getValue("INVENTORY_COST_REVISION").sourceOwner)
        assertEquals(SyncSourceOwner.OPTIMAL, SyncOwnershipRegistry.byAggregateType.getValue("OPTIMAL_MAINTENANCE").sourceOwner)
        val payment = SyncOwnershipRegistry.byAggregateType.getValue("PAYMENT")
        assertTrue("invoiceId" in payment.businessIdentity)
        assertTrue("paymentId" in payment.businessIdentity)
        assertTrue(payment.protectedKeys.any { "PAYMENT:paymentId" == it })
    }

    @Test
    fun `unknown owner and aggregate fail closed`() {
        try {
            SyncSourceOwner.fromTable("invented_outbox")
            fail("unknown queue must not receive a default owner")
        } catch (failure: UnknownSyncOwnerException) {
            assertTrue(failure.message.orEmpty().startsWith("BLOCKED_OWNER_UNPROVEN"))
        }
        assertFalse("UNKNOWN" in SyncOwnershipRegistry.byAggregateType)
    }

    @Test
    fun `failed blocked rejected and local retained are pending`() {
        listOf("PENDING", "RETRY", "LEASED", "SYNCING", "FAILED", "BLOCKED", "REQUIRES_REVIEW", "REJECTED", "LOCAL_RETAINED")
            .forEach { state -> assertTrue("$state must remain protected", SyncSourceOwner.UNIFIED.isPending(state)) }
        assertFalse(SyncSourceOwner.UNIFIED.isPending("ACKNOWLEDGED"))
        assertFalse(SyncSourceOwner.FINANCIAL.isPending("SYNCED"))
        assertFalse(SyncSourceOwner.ATTACHMENT.isPending("COMPLETED"))
        assertFalse(SyncSourceOwner.ATTACHMENT.isPending("CANCELLED"))
    }
}
