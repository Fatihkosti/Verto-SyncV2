package com.verto.app.money

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseMoney334Test {
    @Test fun `expense boundaries convert exactly once to minor units`() {
        assertEquals(1L, Money.fromLegacyDouble(0.01).amountMinor)
        assertEquals(1010L, Money.fromLegacyDouble(10.10).amountMinor)
        assertEquals(99_999_999L, Money.fromLegacyDouble(999_999.99).amountMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NaN is rejected at compatibility boundary`() { Money.fromLegacyDouble(Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun `infinity is rejected at compatibility boundary`() { Money.fromLegacyDouble(Double.POSITIVE_INFINITY) }
}
