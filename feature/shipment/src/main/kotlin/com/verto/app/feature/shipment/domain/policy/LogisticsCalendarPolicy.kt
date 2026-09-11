package com.verto.app.feature.shipment.domain.policy

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

interface LogisticsCalendarPolicy {
    val id: String
    fun isWorkingDay(epochMillis: Long, zoneId: ZoneId): Boolean
}

/** Current product rule. Kept in domain so country/holiday policies can replace it without UI logic. */
object FridayOffLogisticsCalendarPolicy : LogisticsCalendarPolicy {
    override val id: String = "FRIDAY_OFF"
    override fun isWorkingDay(epochMillis: Long, zoneId: ZoneId): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).dayOfWeek != DayOfWeek.FRIDAY
}

object LogisticsCalendarPolicyRegistry {
    fun resolve(id: String): LogisticsCalendarPolicy = when (id) {
        FridayOffLogisticsCalendarPolicy.id -> FridayOffLogisticsCalendarPolicy
        else -> error("Unknown logistics calendar policy: $id")
    }
}
