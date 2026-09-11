package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV229FoundationTest {
    @Test
    fun journey_start_is_not_movement() {
        assertTrue(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.READY, LogisticsShipmentState.WAITING_DEPARTURE))
        assertFalse(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.READY, LogisticsShipmentState.IN_TRANSIT))
        assertTrue(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.WAITING_DEPARTURE, LogisticsShipmentState.IN_TRANSIT))
    }

    @Test
    fun foreign_currency_requires_rate_date_and_fixed_direction() {
        LogisticsCurrencyPolicy.requireRate(LogisticsCurrencyPolicy.normalizeIso4217("usd"), BigDecimal("700"), 123L)
        runCatching { LogisticsCurrencyPolicy.requireRate(LogisticsCurrencyPolicy.normalizeIso4217("USD"), BigDecimal("700"), null) }
            .onSuccess { error("exchange-rate date must be required") }
    }

    @Test
    fun friday_is_excluded_by_customs_calendar() {
        val zone = ZoneId.of("Africa/Khartoum")
        val friday = Instant.parse("2026-08-21T10:00:00Z")
        val thursday = Instant.parse("2026-08-20T10:00:00Z")
        assertFalse(FridayOffLogisticsCalendarPolicy.isWorkingDay(friday.toEpochMilli(), zone))
        assertTrue(FridayOffLogisticsCalendarPolicy.isWorkingDay(thursday.toEpochMilli(), zone))
    }
}
