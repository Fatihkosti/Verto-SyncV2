package com.verto.app.feature.payment.application

import com.verto.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleInvoiceSettlementPolicy349Test {
    @Test
    fun `full amount derives fully paid without explicit cash choice`() {
        val result = SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("500"))
        assertEquals(SaleSettlementKind.FULLY_PAID, result.kind)
        assertEquals(0L, result.remaining.amountMinor)
    }

    @Test
    fun `zero or partial amount derives credit`() {
        val zero = SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.zero())
        val partial = SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("125"))
        assertEquals(SaleSettlementKind.CREDIT, zero.kind)
        assertEquals(SaleSettlementKind.CREDIT, partial.kind)
        assertEquals(Money.parse("375").amountMinor, partial.remaining.amountMinor)
    }

    @Test
    fun `paid amount above total is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleInvoiceSettlementPolicy.evaluate(Money.parse("500"), Money.parse("501"))
        }
    }
}
