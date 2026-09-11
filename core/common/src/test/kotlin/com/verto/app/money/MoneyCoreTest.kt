package com.verto.app.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyCoreTest {
    @Test fun `three times point one is exactly thirty minor units`() {
        assertEquals(30L, (Money.parse("0.1") * Quantity.of(3)).amountMinor)
    }

    @Test fun `arabic and english numerals normalize to same money`() {
        assertEquals(Money.parse("1,250.50"), Money.parse("١٬٢٥٠٫٥٠"))
    }

    @Test fun `blank and malformed numbers fail closed`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Quantity.parseOrNull("abc"))
        assertNull(Quantity.parseOrNull(""))
    }

    @Test fun `exchange rate is fixed precision`() {
        val rate = ExchangeRate.parse("2500.125", quoteCurrency = "SDG")
        val converted = rate.convert(Money.parse("2"))
        assertEquals(500_025L, converted.amountMinor)
        assertEquals("2500.125", rate.asDecimal().toPlainString())
    }

    @Test(expected = ArithmeticException::class)
    fun `money multiplication detects overflow`() {
        Money.ofMinor(Long.MAX_VALUE) * Quantity.of(2)
    }
}
