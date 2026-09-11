package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * v240 station/customs execution contract.
 *
 * v234+ plans customs as an event after a real route station. Legacy rows may still contain a
 * CUSTOMS milestone; those rows remain executable without reintroducing CUSTOMS into new routes.
 */
object LogisticsV240ExecutionPolicy {
    fun customsHostMilestone(aggregate: LogisticsShipmentAggregate): LogisticsMilestone? {
        aggregate.customsPlan?.let { plan ->
            return aggregate.milestones.singleOrNull { it.id == plan.afterStationId }
        }
        aggregate.shipment.customsMilestoneId?.let { activeId ->
            aggregate.milestones.singleOrNull { it.id == activeId }?.let { return it }
        }
        return aggregate.milestones
            .filter { it.type == LogisticsMilestoneType.CUSTOMS }
            .sortedBy { it.order }
            .firstOrNull { it.customsCompletedAt == null }
            ?: aggregate.milestones.filter { it.type == LogisticsMilestoneType.CUSTOMS }.maxByOrNull { it.order }
    }

    fun isCustomsHost(aggregate: LogisticsShipmentAggregate, milestone: LogisticsMilestone): Boolean =
        customsHostMilestone(aggregate)?.id == milestone.id

    fun requireCustomsHost(aggregate: LogisticsShipmentAggregate, milestone: LogisticsMilestone) {
        require(isCustomsHost(aggregate, milestone)) {
            "Customs can run only after the designated station"
        }
    }

    fun requireCustomsCompletedBeforeLoading(aggregate: LogisticsShipmentAggregate, milestone: LogisticsMilestone) {
        if (!isCustomsHost(aggregate, milestone)) return
        require(milestone.customsStartedAt != null && milestone.customsCompletedAt != null) {
            "Customs must be completed at the designated station before loading onward"
        }
    }

    fun customsTiming(aggregate: LogisticsShipmentAggregate, now: Long): LogisticsCustomsTiming? {
        val plan = aggregate.customsPlan ?: return null
        val milestone = aggregate.milestones.singleOrNull { it.id == plan.afterStationId } ?: return null
        val startedAt = milestone.customsStartedAt ?: return null
        val endAt = milestone.customsCompletedAt ?: now
        val zoneId = runCatching { ZoneId.of(aggregate.shipment.eventTimezoneId) }.getOrDefault(ZoneId.of("UTC"))
        val calendar = LogisticsCalendarPolicyRegistry.resolve(aggregate.shipment.customsCalendarPolicyId)
        val elapsed = workingElapsedMillis(startedAt, endAt, zoneId, calendar)
        val expected = plan.expectedDurationMinutes.toLong() * 60_000L
        return LogisticsCustomsTiming(
            startedAt = startedAt,
            completedAt = milestone.customsCompletedAt,
            expectedWorkingMillis = expected,
            elapsedWorkingMillis = elapsed,
            delayMillis = (elapsed - expected).coerceAtLeast(0L),
        )
    }

    fun workingElapsedMillis(
        startAt: Long,
        endAt: Long,
        zoneId: ZoneId,
        calendarPolicy: LogisticsCalendarPolicy,
    ): Long {
        require(startAt >= 0L && endAt >= startAt) { "Invalid customs time range" }
        if (startAt == endAt) return 0L
        val finish = Instant.ofEpochMilli(endAt).atZone(zoneId)
        var cursor = Instant.ofEpochMilli(startAt).atZone(zoneId)
        var total = 0L
        while (cursor.isBefore(finish)) {
            val nextDay = cursor.toLocalDate().plusDays(1).atStartOfDay(zoneId)
            val segmentEnd = if (nextDay.isBefore(finish)) nextDay else finish
            if (calendarPolicy.isWorkingDay(cursor.toInstant().toEpochMilli(), zoneId)) {
                total += Duration.between(cursor, segmentEnd).toMillis()
            }
            cursor = segmentEnd
        }
        return total
    }
}

data class LogisticsCustomsTiming(
    val startedAt: Long,
    val completedAt: Long?,
    val expectedWorkingMillis: Long,
    val elapsedWorkingMillis: Long,
    val delayMillis: Long,
)
