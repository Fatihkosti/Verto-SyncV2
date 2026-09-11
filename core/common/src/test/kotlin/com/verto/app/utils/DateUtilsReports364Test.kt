package com.verto.app.utils

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsReports364Test {

    @Test
    fun monthRange_isMonthToDate_notFutureMonthEnd() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 24, 15, 30, 45)
            set(Calendar.MILLISECOND, 321)
        }.timeInMillis
        val expectedStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val range = DateUtils.rangeForPeriod(ReportPeriod.MONTH, now)

        assertEquals(expectedStart, range.first)
        assertEquals(now, range.second)
    }

    @Test
    fun todayRange_startsAtExactLocalMidnight() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 24, 15, 30, 45)
            set(Calendar.MILLISECOND, 987)
        }.timeInMillis
        val expectedStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val range = DateUtils.rangeForPeriod(ReportPeriod.TODAY, now)

        assertEquals(expectedStart, range.first)
        assertEquals(now, range.second)
    }
}
