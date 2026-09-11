package com.verto.app.feature.shipment.domain.policy

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LogisticsCurrencyPolicy370Test {
    @Test
    fun `foreign purchase unit price is converted to SDG before landed cost`() {
        val base = LogisticsCurrencyPolicy.toBaseCurrencyAmount(
            amount = BigDecimal("100"),
            currency = "USD",
            exchangeRate = BigDecimal("2500"),
            exchangeRateDate = 1L,
        )
        assertEquals(0, base.compareTo(BigDecimal("250000")))
    }

    @Test
    fun `foreign conversion fails closed without a valid rate`() {
        assertFailsWith<IllegalArgumentException> {
            LogisticsCurrencyPolicy.toBaseCurrencyAmount(
                amount = BigDecimal("100"),
                currency = "USD",
                exchangeRate = BigDecimal.ZERO,
                exchangeRateDate = 1L,
            )
        }
    }
}
