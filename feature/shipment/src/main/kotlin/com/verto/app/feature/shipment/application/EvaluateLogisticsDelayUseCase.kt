package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.application.model.LogisticsDelayClockKind
import com.verto.app.feature.shipment.application.model.LogisticsDelayContactReadModel
import com.verto.app.feature.shipment.application.model.LogisticsDelayContactSource
import com.verto.app.feature.shipment.application.model.LogisticsDelayReadModel
import com.verto.app.feature.shipment.application.model.LogisticsDelayTimingReadModel
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class EvaluateLogisticsDelayUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
        now: Long = clock.now(),
    ): LogisticsDelayReadModel {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(now >= 0L) { "now must not be negative" }
        val aggregate = store.getShipment(organizationId, shipmentId)
            ?: error("Logistics shipment not found")
        val partners = runCatching { store.listPartners(organizationId) }.getOrDefault(emptyList())
        return evaluate(aggregate, now, partners)
    }

    fun evaluate(
        aggregate: LogisticsShipmentAggregate,
        now: Long,
        partners: List<LogisticsPartner> = emptyList(),
    ): LogisticsDelayReadModel {
        require(now >= 0L) { "now must not be negative" }
        val orderedMilestones = aggregate.milestones.sortedBy { it.order }
        val activeLeg = aggregate.legs
            .asSequence()
            .filter { it.status == LogisticsLegStatus.IN_TRANSIT && it.supersededAt == null }
            .minByOrNull { it.sequence }
        val activeMilestone = if (activeLeg == null) {
            orderedMilestones.lastOrNull { it.arrivedAt != null && it.departedAt == null }
        } else null

        val customsHost = LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate)
        val clockResult = when {
            activeLeg?.actualDepartureAt != null &&
                (activeLeg.expectedTransitMinutes != null || activeLeg.expectedTransitDays != null) ->
                transitClock(activeLeg, orderedMilestones, now)
            activeMilestone?.id == customsHost?.id && activeMilestone?.customsStartedAt != null && aggregate.customsPlan != null ->
                customsClock(aggregate, now)
            activeMilestone?.arrivedAt != null && activeMilestone.expectedStayDays != null ->
                dwellClock(activeMilestone, now)
            else -> ClockResult.none(currentStage(aggregate, activeLeg, activeMilestone))
        }

        val contact = resolveContact(
            aggregate = aggregate,
            activeLeg = activeLeg,
            activeMilestone = activeMilestone,
            partners = partners,
        )
        val delayMillis = positiveDifference(clockResult.elapsedMillis, clockResult.expectedMillis)
        return LogisticsDelayReadModel(
            level = clockResult.level,
            clockKind = clockResult.kind,
            timing = LogisticsDelayTimingReadModel(
                startedAt = clockResult.startedAt,
                expectedMillis = clockResult.expectedMillis,
                elapsedMillis = clockResult.elapsedMillis,
                delayMillis = delayMillis,
                delayDays = ceilDays(delayMillis),
                veryLateThresholdAt = clockResult.startedAt?.let { safeAdd(it, safeMultiply(clockResult.expectedMillis, 2L)) },
            ),
            currentStage = clockResult.stage,
            contact = contact,
            lastOperationalUpdateAt = lastOperationalUpdateAt(aggregate),
        )
    }

    private fun transitClock(
        leg: LogisticsShipmentLeg,
        milestones: List<LogisticsMilestone>,
        now: Long,
    ): ClockResult {
        val startedAt = requireNotNull(leg.actualDepartureAt)
        val expected = transitExpectedMillis(leg)
        val end = leg.actualArrivalAt ?: now
        val elapsed = elapsedMillis(startedAt, end)
        val from = milestones.firstOrNull { it.id == leg.fromMilestoneId }?.placeName
            ?.takeIf(String::isNotBlank)
            ?: milestones.firstOrNull { it.id == leg.fromMilestoneId }?.location
            ?: "المحطة السابقة"
        val to = milestones.firstOrNull { it.id == leg.toMilestoneId }?.placeName
            ?.takeIf(String::isNotBlank)
            ?: milestones.firstOrNull { it.id == leg.toMilestoneId }?.location
            ?: "المحطة التالية"
        return ClockResult(
            kind = LogisticsDelayClockKind.TRANSIT,
            startedAt = startedAt,
            expectedMillis = expected,
            elapsedMillis = elapsed,
            level = classify(expected, elapsed),
            stage = "$from → $to",
        )
    }

    private fun customsClock(aggregate: LogisticsShipmentAggregate, now: Long): ClockResult {
        val timing = requireNotNull(LogisticsV240ExecutionPolicy.customsTiming(aggregate, now))
        val host = requireNotNull(LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate))
        val place = aggregate.customsPlan?.checkpointName?.takeIf(String::isNotBlank)
            ?: host.placeName.takeIf(String::isNotBlank)
            ?: host.location
        return ClockResult(
            kind = LogisticsDelayClockKind.DWELL,
            startedAt = timing.startedAt,
            expectedMillis = timing.expectedWorkingMillis,
            elapsedMillis = timing.elapsedWorkingMillis,
            level = classify(timing.expectedWorkingMillis, timing.elapsedWorkingMillis),
            stage = "الجمارك: $place",
        )
    }

    private fun dwellClock(milestone: LogisticsMilestone, now: Long): ClockResult {
        val startedAt = requireNotNull(milestone.arrivedAt)
        val expected = daysToMillis(requireNotNull(milestone.expectedStayDays))
        val end = milestone.departedAt ?: now
        val elapsed = elapsedMillis(startedAt, end)
        val place = milestone.placeName.takeIf(String::isNotBlank) ?: milestone.location
        val prefix = if (milestone.type == LogisticsMilestoneType.CUSTOMS) "الجمارك" else "المحطة"
        return ClockResult(
            kind = LogisticsDelayClockKind.DWELL,
            startedAt = startedAt,
            expectedMillis = expected,
            elapsedMillis = elapsed,
            level = classify(expected, elapsed),
            stage = "$prefix: $place",
        )
    }

    private fun resolveContact(
        aggregate: LogisticsShipmentAggregate,
        activeLeg: LogisticsShipmentLeg?,
        activeMilestone: LogisticsMilestone?,
        partners: List<LogisticsPartner>,
    ): LogisticsDelayContactReadModel? {
        val partnerById = partners.associateBy { it.id }

        val custodyPartners = LogisticsCustodyResolver.currentForAllSources(aggregate)
            .values
            .asSequence()
            .filter { it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER }
            .mapNotNull { it.holderId }
            .distinct()
        for (partnerId in custodyPartners) {
            val partner = partnerById[partnerId] ?: continue
            val phone = usablePhone(partner.phone) ?: continue
            return LogisticsDelayContactReadModel(
                displayName = partner.name,
                role = "حائز المسؤولية الحالي",
                phone = phone,
                source = LogisticsDelayContactSource.CUSTODY_HOLDER,
            )
        }

        activeLeg?.let { leg ->
            usablePhone(leg.representativePhoneSnapshot)?.let { phone ->
                return LogisticsDelayContactReadModel(
                    displayName = leg.representativeNameSnapshot?.takeIf(String::isNotBlank)
                        ?: partnerById[leg.carrierPartnerId]?.name
                        ?: "ممثل الناقل",
                    role = "ممثل الناقل الحالي",
                    phone = phone,
                    source = LogisticsDelayContactSource.LEG_CARRIER,
                )
            }
            partnerById[leg.carrierPartnerId]?.let { partner ->
                usablePhone(partner.phone)?.let { phone ->
                    return LogisticsDelayContactReadModel(
                        displayName = partner.name,
                        role = "الناقل الحالي",
                        phone = phone,
                        source = LogisticsDelayContactSource.LEG_CARRIER,
                    )
                }
            }
        }

        val customs = activeMilestone?.takeIf { LogisticsV240ExecutionPolicy.isCustomsHost(aggregate, it) }
            ?: LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate)?.takeIf {
                it.customsStartedAt != null && it.customsCompletedAt == null
            }
        customs?.let { milestone ->
            usablePhone(milestone.customsBrokerPhoneSnapshot)?.let { phone ->
                return LogisticsDelayContactReadModel(
                    displayName = milestone.customsBrokerNameSnapshot?.takeIf(String::isNotBlank) ?: "المخلص الجمركي",
                    role = "المخلص الجمركي",
                    phone = phone,
                    source = LogisticsDelayContactSource.CUSTOMS_BROKER,
                )
            }
            milestone.customsBrokerPartnerId?.let { partnerId ->
                partnerById[partnerId]?.let { partner ->
                    usablePhone(partner.phone)?.let { phone ->
                        return LogisticsDelayContactReadModel(
                            displayName = partner.name,
                            role = "المخلص الجمركي",
                            phone = phone,
                            source = LogisticsDelayContactSource.CUSTOMS_BROKER,
                        )
                    }
                }
            }
        }

        return aggregate.shipment.assignee?.let { assignee ->
            LogisticsDelayContactReadModel(
                displayName = assignee.employeeName,
                role = "مسؤول متابعة الشحنة",
                phone = null,
                source = LogisticsDelayContactSource.FOLLOW_UP_EMPLOYEE,
            )
        }
    }

    private fun currentStage(
        aggregate: LogisticsShipmentAggregate,
        activeLeg: LogisticsShipmentLeg?,
        activeMilestone: LogisticsMilestone?,
    ): String = when {
        activeLeg != null -> {
            val from = aggregate.milestones.firstOrNull { it.id == activeLeg.fromMilestoneId }?.location ?: aggregate.shipment.sourceLocation
            val to = aggregate.milestones.firstOrNull { it.id == activeLeg.toMilestoneId }?.location ?: aggregate.shipment.destinationLocation
            "$from → $to"
        }
        activeMilestone != null -> activeMilestone.placeName.takeIf(String::isNotBlank) ?: activeMilestone.location
        else -> aggregate.shipment.destinationLocation
    }

    private fun lastOperationalUpdateAt(aggregate: LogisticsShipmentAggregate): Long? = buildList {
        aggregate.shipment.startedAt?.let(::add)
        aggregate.milestones.forEach { milestone ->
            milestone.arrivedAt?.let(::add)
            milestone.unloadedAt?.let(::add)
            milestone.loadedAt?.let(::add)
            milestone.departedAt?.let(::add)
            milestone.customsStartedAt?.let(::add)
            milestone.customsCompletedAt?.let(::add)
        }
        aggregate.legs.forEach { leg ->
            leg.actualDepartureAt?.let(::add)
            leg.actualArrivalAt?.let(::add)
        }
        aggregate.custodyHandoffs.forEach { handoff ->
            add(handoff.transferredAt)
            add(handoff.receivedAt)
        }
    }.maxOrNull()

    private fun classify(expectedMillis: Long, elapsedMillis: Long): LogisticsDelayLevel {
        if (expectedMillis <= 0L) return LogisticsDelayLevel.NORMAL
        if (elapsedMillis <= expectedMillis) return LogisticsDelayLevel.NORMAL
        val doubleExpected = safeMultiply(expectedMillis, 2L)
        return if (elapsedMillis <= doubleExpected) LogisticsDelayLevel.LATE else LogisticsDelayLevel.VERY_LATE
    }

    private fun transitExpectedMillis(leg: LogisticsShipmentLeg): Long {
        val minutes = leg.expectedTransitMinutes ?: leg.expectedTransitDays?.times(24 * 60) ?: 0
        return if (minutes <= 0) 0L else safeMultiply(minutes.toLong(), MINUTE_MILLIS)
    }

    private fun daysToMillis(days: Int): Long = if (days <= 0) 0L else safeMultiply(days.toLong(), DAY_MILLIS)

    private fun elapsedMillis(start: Long, end: Long): Long {
        if (end <= start) return 0L
        return try {
            Math.subtractExact(end, start)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    private fun positiveDifference(value: Long, baseline: Long): Long {
        if (value <= baseline) return 0L
        return try {
            Math.subtractExact(value, baseline)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    private fun safeMultiply(value: Long, factor: Long): Long {
        if (value <= 0L || factor <= 0L) return 0L
        return if (value > Long.MAX_VALUE / factor) Long.MAX_VALUE else value * factor
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (left < 0L || right < 0L) return Long.MAX_VALUE
        return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    private fun ceilDays(millis: Long): Long {
        if (millis <= 0L) return 0L
        val whole = millis / DAY_MILLIS
        return if (millis % DAY_MILLIS == 0L) whole else whole + 1L
    }

    private fun usablePhone(raw: String?): String? = raw
        ?.trim()
        ?.takeIf { value -> value.count(Char::isDigit) >= MIN_PHONE_DIGITS }

    private data class ClockResult(
        val kind: LogisticsDelayClockKind,
        val startedAt: Long?,
        val expectedMillis: Long,
        val elapsedMillis: Long,
        val level: LogisticsDelayLevel,
        val stage: String,
    ) {
        companion object {
            fun none(stage: String) = ClockResult(
                kind = LogisticsDelayClockKind.NONE,
                startedAt = null,
                expectedMillis = 0L,
                elapsedMillis = 0L,
                level = LogisticsDelayLevel.NORMAL,
                stage = stage,
            )
        }
    }

    private companion object {
        const val MINUTE_MILLIS: Long = 60_000L
        const val DAY_MILLIS: Long = 86_400_000L
        const val MIN_PHONE_DIGITS: Int = 6
    }
}
