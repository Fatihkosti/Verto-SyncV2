package com.verto.app.feature.dashboard.application

import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.TimeZone

/**
 * Canonical Benzine competition week boundary.
 * A week starts every Friday at 09:00 in Asia/Riyadh.
 */
object BenzineWeekPolicy {
    const val TIME_ZONE_ID: String = "Asia/Riyadh"

    fun weekStartOf(epochMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE_ID)).apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        if (calendar.timeInMillis > epochMillis) {
            calendar.add(Calendar.DAY_OF_MONTH, -7)
        }
        return calendar.timeInMillis
    }

    fun currentWeekStart(nowMillis: Long): Long = weekStartOf(nowMillis)

    fun previousWeekStart(nowMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE_ID)).apply {
            timeInMillis = currentWeekStart(nowMillis)
            add(Calendar.DAY_OF_MONTH, -7)
        }
        return calendar.timeInMillis
    }

    fun parseIsoMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrElse { runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
    }
}
