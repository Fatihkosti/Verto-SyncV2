package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySyncV2ContractTest {
    private val local = PartyMutationV2("op", PartyAggregateType.ROLE, "party", 4, payload = mapOf("role" to "CUSTOMER"))

    @Test fun `same operation replay is acknowledged without another write`() {
        assertEquals(PartyConflictDecision.ACK_REPLAY, PartySyncV2Policy.decide(local, remote(5, "op")))
    }
    @Test fun `stale revision is recorded rather than silently overwritten`() {
        assertEquals(PartyConflictDecision.RECORD_STALE_CONFLICT, PartySyncV2Policy.decide(local, remote(5, null)))
    }
    @Test fun `dependency order places tombstones last`() {
        assertEquals(PartyAggregateType.IDENTITY, PartySyncV2Policy.pushOrder.first())
        assertEquals(PartyAggregateType.TOMBSTONE, PartySyncV2Policy.pushOrder.last())
    }
    @Test fun `authorization and validation errors are not retried`() {
        assertFalse(PartySyncV2Policy.retryable(401)); assertFalse(PartySyncV2Policy.retryable(403)); assertFalse(PartySyncV2Policy.retryable(409))
        assertTrue(PartySyncV2Policy.retryable(429)); assertTrue(PartySyncV2Policy.retryable(503))
    }
    private fun remote(revision: Long, operation: String?) = PartyRemoteVersionV2("party", revision, "2026-08-20T00:00:00Z", operation, payload = emptyMap())
}
