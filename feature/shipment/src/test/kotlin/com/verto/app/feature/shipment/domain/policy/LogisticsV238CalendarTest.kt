package com.verto.app.feature.shipment.domain.policy

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV238CalendarTest {
    @Test
    fun `friday is excluded from customs working days`() {
        val zone = ZoneId.of("Africa/Khartoum")
        val friday = LocalDate.of(2026, 8, 21).atStartOfDay(zone).toInstant().toEpochMilli()
        val thursday = LocalDate.of(2026, 8, 20).atStartOfDay(zone).toInstant().toEpochMilli()

        assertFalse(FridayOffLogisticsCalendarPolicy.isWorkingDay(friday, zone))
        assertTrue(FridayOffLogisticsCalendarPolicy.isWorkingDay(thursday, zone))
    }
}
