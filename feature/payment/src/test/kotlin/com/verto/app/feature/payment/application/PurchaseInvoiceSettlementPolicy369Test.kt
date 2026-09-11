package com.verto.app.feature.payment.application

import com.verto.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseInvoiceSettlementPolicy369Test {
    @Test
    fun `full amount derives fully paid purchase`() {
        val result = PurchaseInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("500"))
        assertEquals(PurchaseSettlementKind.FULLY_PAID, result.kind)
        assertEquals(0L, result.remaining.amountMinor)
    }

    @Test
    fun `zero or partial amount derives supplier credit`() {
        val zero = PurchaseInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.zero())
        val partial = PurchaseInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("125"))
        assertEquals(PurchaseSettlementKind.CREDIT, zero.kind)
        assertEquals(PurchaseSettlementKind.CREDIT, partial.kind)
        assertEquals(Money.parse("375").amountMinor, partial.remaining.amountMinor)
    }

    @Test
    fun `paid amount above purchase total is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("501"))
        }
    }
}
