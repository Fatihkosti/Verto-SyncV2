package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.application.port.SupplierOrderEvidence
import com.verto.app.feature.party.application.port.SupplierOrderLineEvidence
import com.verto.app.feature.party.application.port.SupplierPriceEvidence
import com.verto.app.feature.party.application.port.SupplierReceiptEvidence
import com.verto.app.feature.party.application.port.SupplierReturnEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierIntelligenceEngine346Test {
    private val day = 86_400_000L

    @Test
    fun `supplier score uses quality fill price lead time and promised delivery`() {
        val rows = listOf(
            order("1", "A", 1 * day, 6 * day, 5 * day, 10, 0, 100, 100),
            order("2", "A", 10 * day, 15 * day, 14 * day, 10, 0, 100, 105),
            order("3", "A", 20 * day, 25 * day, 24 * day, 10, 0, 100, 100),
        )
        val snapshot = SupplierIntelligenceEngine.evaluate(rows, 40 * day)

        assertEquals(10_000, snapshot.qualityRateBps)
        assertEquals(10_000, snapshot.acceptedFillRateBps)
        assertEquals(10_000, snapshot.onTimeDeliveryRateBps)
        assertEquals(166, snapshot.priceVarianceBps)
        assertEquals(3_050L, snapshot.matchedInvoiceCostByCurrencyMinor["SDG"])
        assertTrue(snapshot.scoreBps!! >= 8_500)
    }

    @Test
    fun `missing promised dates never invent on time delivery`() {
        val rows = listOf(
            order("1", "A", 1 * day, null, 5 * day, 10, 0, 100, 100),
            order("2", "A", 10 * day, null, 14 * day, 10, 0, 100, 100),
        )
        val snapshot = SupplierIntelligenceEngine.evaluate(rows, 40 * day)

        assertNull(snapshot.onTimeDeliveryRateBps)
        assertEquals(0, snapshot.promisedDeliveryCoverageBps)
        assertTrue("PROMISED_DELIVERY_DATA_INCOMPLETE" in snapshot.scoreReasons)
    }

    @Test
    fun `multi currency history never mixes price variance amounts`() {
        val rows = listOf(
            order("1", "A", 1 * day, 6 * day, 5 * day, 10, 0, 100, 110, "SDG"),
            order("2", "A", 10 * day, 15 * day, 14 * day, 10, 0, 100, 110, "USD"),
        )
        val snapshot = SupplierIntelligenceEngine.evaluate(rows, 40 * day)

        assertNull(snapshot.priceVarianceBps)
        assertTrue("MULTI_CURRENCY_PRICE_VARIANCE_UNAVAILABLE" in snapshot.scoreReasons)
        assertEquals(setOf("SDG", "USD"), snapshot.currencies)
    }

    @Test
    fun `purchase returns reduce effective quality and trigger review`() {
        val rows = listOf(
            order("1", "A", 1 * day, 6 * day, 5 * day, 10, 0, 100, 100, returns = 2),
            order("2", "A", 10 * day, 15 * day, 14 * day, 10, 0, 100, 100),
            order("3", "A", 20 * day, 25 * day, 24 * day, 10, 0, 100, 100),
        )
        val snapshot = SupplierIntelligenceEngine.evaluate(rows, 40 * day)

        assertEquals(666, snapshot.purchaseReturnRateBps)
        assertEquals(10_000, snapshot.qualityRateBps)
        assertEquals(SupplierRecommendedAction.REVIEW_QUALITY, snapshot.recommendedAction)
    }

    @Test
    fun `best supplier for item requires comparable evidence and ranks by performance`() {
        val a = listOf(
            order("1", "A", 1 * day, 6 * day, 5 * day, 10, 0, 100, 100),
            order("2", "A", 10 * day, 15 * day, 14 * day, 10, 0, 100, 100),
        )
        val b = listOf(
            order("3", "B", 1 * day, 6 * day, 9 * day, 8, 2, 100, 125),
            order("4", "B", 10 * day, 15 * day, 19 * day, 8, 2, 100, 125),
        )
        val recommendation = SupplierIntelligenceEngine.recommendForItem("item-1", a + b, 40 * day)

        assertEquals("A", recommendation.bestSupplierId)
        assertEquals(listOf("A", "B"), recommendation.candidates.map { it.supplierId })
    }

    private fun order(
        id: String,
        supplier: String,
        created: Long,
        promised: Long?,
        receivedAt: Long,
        accepted: Int,
        rejected: Int,
        poUnit: Long,
        invoiceUnit: Long,
        currency: String = "SDG",
        returns: Int = 0,
    ) = SupplierOrderEvidence(
        orderId = id,
        supplierId = supplier,
        currencyCode = currency,
        status = "RECEIVED",
        createdAt = created,
        promisedDeliveryAt = promised,
        lines = listOf(SupplierOrderLineEvidence("line-$id", "item-1", 10, poUnit)),
        receipts = listOf(
            SupplierReceiptEvidence(
                id = "receipt-$id",
                orderLineId = "line-$id",
                receivedAt = receivedAt,
                receivedQuantity = accepted + rejected,
                acceptedQuantity = accepted,
                rejectedQuantity = rejected,
                unitCostMinor = invoiceUnit,
            )
        ),
        priceMatches = listOf(
            SupplierPriceEvidence(
                id = "match-$id",
                orderLineId = "line-$id",
                invoicedQuantity = 10,
                poUnitPriceMinor = poUnit,
                invoiceUnitPriceMinor = invoiceUnit,
            )
        ),
        returns = if (returns == 0) emptyList() else listOf(
            SupplierReturnEvidence("return-$id", "item-1", returns, receivedAt + day)
        ),
    )
}
