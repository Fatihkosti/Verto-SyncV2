package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviousMutationOutcomeTest {
    @Test
    fun `saved bytes are retried only under their matching hash`() {
        val wire = "{\"mutationId\":\"old\"}"
        assertEquals(
            PreviousMutationDisposition.RETRY_EXACT_FROZEN_BYTES,
            classifyPreviousMutation(evidence(wire = wire, wireHash = sha256Utf8(wire))),
        )
        assertEquals(
            PreviousMutationDisposition.OUTCOME_UNKNOWN,
            classifyPreviousMutation(evidence(wire = wire, wireHash = "0".repeat(64))),
        )
    }

    @Test
    fun `matching receipt is usable but missing evidence stays unknown`() {
        assertEquals(
            PreviousMutationDisposition.APPLY_MATCHING_RECEIPT,
            classifyPreviousMutation(evidence(wireHash = "a".repeat(64), receiptId = "old", receiptHash = "a".repeat(64))),
        )
        assertEquals(PreviousMutationDisposition.OUTCOME_UNKNOWN, classifyPreviousMutation(evidence()))
        assertEquals(
            PreviousMutationDisposition.OUTCOME_UNKNOWN,
            classifyPreviousMutation(evidence(wireHash = "a".repeat(64), receiptId = "different", receiptHash = "a".repeat(64))),
        )
    }

    private fun evidence(
        wire: String? = null,
        wireHash: String? = null,
        receiptId: String? = null,
        receiptHash: String? = null,
    ) = PreviousMutationEvidence("old", "business-1", wire, wireHash, receiptId, receiptHash)
}
