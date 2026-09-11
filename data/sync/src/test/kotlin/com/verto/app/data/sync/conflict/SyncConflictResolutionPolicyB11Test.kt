package com.verto.app.data.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncConflictResolutionPolicyB11Test {
    @Test
    fun `mutable proven conflict on displayed version is eligible`() {
        SyncConflictResolutionPolicy.requireDecisionAllowed(
            aggregateType = "INVENTORY_ITEM",
            state = "OPEN",
            outcomeProof = "PROVEN_CONFLICT",
            evidenceServerVersion = 7,
            displayedServerVersion = 7,
            latestKnownServerVersion = 7,
        )
    }

    @Test
    fun `immutable payment cannot be replaced`() {
        assertCode("DOMAIN_CORRECTION_REQUIRED") {
            SyncConflictResolutionPolicy.requireDecisionAllowed("PAYMENT", "OPEN", "PROVEN_CONFLICT", 7, 7, 7)
        }
    }

    @Test
    fun `outcome unknown blocks both user decision paths`() {
        assertCode("OUTCOME_UNKNOWN") {
            SyncConflictResolutionPolicy.requireDecisionAllowed("INVENTORY_ITEM", "OPEN", "OUTCOME_UNKNOWN", 7, 7, 7)
        }
    }

    @Test
    fun `changed server version invalidates displayed decision`() {
        assertCode("CONFLICT_REMOTE_VERSION_CHANGED") {
            SyncConflictResolutionPolicy.requireDecisionAllowed("INVENTORY_ITEM", "OPEN", "PROVEN_CONFLICT", 7, 7, 8)
        }
    }

    private fun assertCode(expected: String, block: () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: SyncConflictResolutionException) {
            assertEquals(expected, e.code)
        }
    }
}
