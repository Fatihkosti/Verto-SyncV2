package com.verto.app.feature.shipment.presentation.logisticsv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV241FinalReceivingTest {
    @Test
    fun `complete receipt accepts every remaining unit`() {
        val draft = buildFinalReceivingDraft(
            lines = listOf(line("a", expected = 10, already = 2), line("b", expected = 4)),
            receivedCompletely = true,
            missingInputs = mapOf("a" to "7"),
        )

        assertTrue(draft.isValid)
        assertEquals(8, draft.lines.single { it.shipmentLineId == "a" }.acceptedQuantity)
        assertEquals(4, draft.lines.single { it.shipmentLineId == "b" }.acceptedQuantity)
        assertTrue(draft.lines.all { it.missingAfterBatch == 0 })
    }

    @Test
    fun `short receipt keeps blank fields unaffected and accepts only delivered quantity`() {
        val draft = buildFinalReceivingDraft(
            lines = listOf(line("a", expected = 10), line("b", expected = 5)),
            receivedCompletely = false,
            missingInputs = mapOf("a" to "3", "b" to ""),
        )

        assertTrue(draft.isValid)
        assertEquals(7, draft.lines.single { it.shipmentLineId == "a" }.acceptedQuantity)
        assertEquals(3, draft.lines.single { it.shipmentLineId == "a" }.missingAfterBatch)
        assertEquals(5, draft.lines.single { it.shipmentLineId == "b" }.acceptedQuantity)
        assertEquals(0, draft.lines.single { it.shipmentLineId == "b" }.missingAfterBatch)
    }

    @Test
    fun `short receipt requires at least one shortage`() {
        val draft = buildFinalReceivingDraft(
            lines = listOf(line("a", expected = 3)),
            receivedCompletely = false,
            missingInputs = emptyMap(),
        )
        assertFalse(draft.isValid)
    }

    @Test
    fun `shortage larger than remaining quantity is invalid`() {
        val draft = buildFinalReceivingDraft(
            lines = listOf(line("a", expected = 3, already = 1)),
            receivedCompletely = false,
            missingInputs = mapOf("a" to "3"),
        )
        assertFalse(draft.isValid)
    }

    @Test
    fun `saved baseline survives process death after inventory posting`() {
        val currentAfterPosting = listOf(line("a", expected = 10, already = 7))
        val saved = LogisticsFinalReceivingDraftSnapshot(
            shipmentId = "shipment",
            requestId = "stable-request",
            receivedCompletely = false,
            missingQuantities = mapOf("a" to "3"),
            lines = listOf(
                LogisticsFinalReceivingLineSnapshot("a", "inv-a", "#a", "Item a", 10, 0),
            ),
        )

        val restored = restoreFinalReceivingLines(currentAfterPosting, saved)
        val draft = buildFinalReceivingDraft(restored, false, saved.missingQuantities)

        assertEquals(0, restored.single().alreadyReceivedQuantity)
        assertEquals(7, draft.lines.single().acceptedQuantity)
        assertEquals(3, draft.lines.single().missingAfterBatch)
        assertTrue(draft.isValid)
    }

    @Test
    fun `overflowing shortage input is invalid instead of being treated as zero`() {
        val draft = buildFinalReceivingDraft(
            lines = listOf(line("a", expected = 3), line("b", expected = 2)),
            receivedCompletely = false,
            missingInputs = mapOf("a" to "999999999999999999999", "b" to "1"),
        )
        assertFalse(draft.isValid)
    }

    private fun line(id: String, expected: Int, already: Int = 0) = LogisticsReceivingLineDraft(
        shipmentLineId = id,
        sourceInvoiceId = "inv-$id",
        sourceInvoiceNumber = "#$id",
        itemName = "Item $id",
        expectedQuantity = expected,
        alreadyReceivedQuantity = already,
    )
}
