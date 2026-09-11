package com.verto.app.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

class MoneyPropertyF251Test {
    @Test
    fun `fixed point add subtract and serialization round trip over deterministic sample`() {
        val random = Random(251)
        repeat(2_000) {
            val a = random.nextLong(-10_000_000_00L, 10_000_000_00L)
            val b = random.nextLong(-10_000_000_00L, 10_000_000_00L)
            val left = Money.ofMinor(a)
            val right = Money.ofMinor(b)
            assertEquals(a + b, (left + right).amountMinor)
            assertEquals(a - b, (left - right).amountMinor)
            assertEquals(left, Money.parse(left.toPlainString()))
        }
    }

    @Test
    fun `legacy decimal boundary rounds once using documented half up rule`() {
        assertEquals(101L, Money.fromMajor(BigDecimal("1.005")).amountMinor)
        assertEquals(-101L, Money.fromMajor(BigDecimal("-1.005")).amountMinor)
    }

    @Test
    fun `rate conversion remains fixed point for deterministic rates`() {
        val amounts = listOf("0.01", "1", "10.25", "999999.99")
        val rates = listOf("1", "0.5", "2.75", "2500.125")
        for (amountText in amounts) for (rateText in rates) {
            val amount = Money.parse(amountText, "USD")
            val rate = ExchangeRate.parse(rateText, "USD", "SDG")
            val expected = amount.toMajorDecimal()
                .multiply(BigDecimal(rateText))
                .setScale(Money.MINOR_SCALE, Money.ROUNDING)
                .movePointRight(Money.MINOR_SCALE)
                .longValueExact()
            assertEquals(expected, rate.convert(amount).amountMinor)
        }
    }
}
