package com.verto.app.utils

import org.junit.Assert.*
import org.junit.Test

class MoneyMathTest {
    @Test fun `decimal addition is rounded exactly`() = assertEquals(0.30, MoneyMath.add(0.1, 0.2), 0.0)
    @Test fun `half up rounding is applied`() = assertEquals(10.01, MoneyMath.round(10.005), 0.0)
    @Test(expected = IllegalArgumentException::class) fun `division by zero is rejected`() { MoneyMath.divide(25.0, 0.0) }
    @Test fun `money sum handles mixed signs`() = with(MoneyMath) { assertEquals(0.0, listOf(10.10, -5.05, -5.05).moneySum(), 0.0) }
    @Test fun `comparison happens after money rounding`() { assertFalse(MoneyMath.isGreaterThan(100.004, 100.0)); assertTrue(MoneyMath.isGreaterThan(100.005, 100.0)) }
    @Test(expected = IllegalArgumentException::class) fun `non finite money is rejected`() { MoneyMath.round(Double.NaN) }
}
