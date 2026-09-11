package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.application.model.LogisticsCarrierMovementReadModel
import com.verto.app.feature.shipment.application.model.LogisticsCarrierSummaryReadModel
import com.verto.app.feature.shipment.application.model.LogisticsCountryMovementReadModel
import com.verto.app.feature.shipment.application.model.LogisticsCountrySummaryReadModel
import com.verto.app.feature.shipment.application.model.LogisticsDelayCountsReadModel
import com.verto.app.feature.shipment.application.model.LogisticsRouteAnalyticsReadModel
import com.verto.app.feature.shipment.application.model.LogisticsRouteStopsReadModel
import com.verto.app.feature.shipment.application.model.LogisticsRouteSummaryReadModel
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import java.math.BigDecimal
import javax.inject.Inject

/** Derives operational route evidence from persisted shipment facts; no analytics table is duplicated. */
class LogisticsRouteAnalyticsUseCase @Inject constructor() {
    operator fun invoke(
        aggregates: Collection<LogisticsShipmentAggregate>,
        nowMillis: Long,
    ): LogisticsRouteAnalyticsReadModel {
        val country = linkedMapOf<String, CountryAccumulator>()
        val carrier = linkedMapOf<String, CarrierAccumulator>()
        val routes = aggregates.map { aggregate ->
            accumulateCountries(aggregate, nowMillis, country)
            accumulateCarriers(aggregate, nowMillis, carrier)
            routeSummary(aggregate, nowMillis)
        }
        return LogisticsRouteAnalyticsReadModel(
            countries = country.values.map(CountryAccumulator::toReadModel)
                .sortedWith(compareByDescending<LogisticsCountrySummaryReadModel> { it.actualMovementCount }
                    .thenBy { it.countryName }),
            carriers = carrier.values.map(CarrierAccumulator::toReadModel)
                .sortedWith(compareByDescending<LogisticsCarrierSummaryReadModel> { it.handledLegCount }
                    .thenBy { it.carrierName ?: it.carrierId }),
            routes = routes.sortedWith(compareByDescending<LogisticsRouteSummaryReadModel> { it.totalElapsedMillis }
                .thenBy { it.shipmentNumber }),
        )
    }

    private fun accumulateCountries(
        aggregate: LogisticsShipmentAggregate,
        nowMillis: Long,
        out: MutableMap<String, CountryAccumulator>,
    ) {
        val milestones = aggregate.milestones.associateBy { it.id }
        aggregate.milestones.filter { it.countryCode.isNotBlank() && it.arrivedAt != null }.forEach { milestone ->
            val acc = out.country(milestone)
            val arrivedAt = requireNotNull(milestone.arrivedAt)
            val elapsed = elapsed(arrivedAt, milestone.departedAt ?: nowMillis)
            acc.dwellMillis += elapsed
            classify(elapsed, milestone.expectedStayDays)?.let(acc::recordDelay)
        }
        aggregate.legs.filter { it.actualDepartureAt != null }.forEach { leg ->
            val from = milestones[leg.fromMilestoneId]
            val to = milestones[leg.toMilestoneId]
            if (from != null && to != null && from.countryCode.isNotBlank() && from.countryCode == to.countryCode) {
                val acc = out.country(from)
                val elapsed = elapsed(leg.actualDepartureAt ?: return@forEach, leg.actualArrivalAt ?: nowMillis)
                acc.movementCount += 1
                acc.transitMillis += elapsed
                classify(elapsed, leg.expectedTransitDays)?.let(acc::recordDelay)
            }
        }
        aggregate.actualCosts().forEach { cost ->
            countryForCost(cost, aggregate, milestones)?.let { out.country(it).scopedCost += cost.baseCurrencyAmount }
        }
    }

    private fun accumulateCarriers(
        aggregate: LogisticsShipmentAggregate,
        nowMillis: Long,
        out: MutableMap<String, CarrierAccumulator>,
    ) {
        val carrierNameById = aggregate.custodyHandoffs
            .flatMap { handoff ->
                listOfNotNull(
                    handoff.fromHolderId?.let { it to handoff.fromHolderNameSnapshot },
                    handoff.toHolderId?.let { it to handoff.toHolderNameSnapshot },
                )
            }.toMap()
        aggregate.legs.filter { it.actualDepartureAt != null }.forEach { leg ->
            val acc = out.getOrPut(leg.carrierPartnerId) {
                CarrierAccumulator(leg.carrierPartnerId, carrierNameById[leg.carrierPartnerId])
            }
            val elapsed = elapsed(leg.actualDepartureAt ?: return@forEach, leg.actualArrivalAt ?: nowMillis)
            acc.handledLegs += 1
            acc.totalTransitMillis += elapsed
            when (classify(elapsed, leg.expectedTransitDays) ?: LogisticsDelayLevel.NORMAL) {
                LogisticsDelayLevel.NORMAL -> acc.normal += 1
                LogisticsDelayLevel.LATE -> acc.late += 1
                LogisticsDelayLevel.VERY_LATE -> acc.veryLate += 1
            }
        }
        val legById = aggregate.legs.associateBy { it.id }
        aggregate.actualCosts().forEach { cost ->
            val carrierId = cost.legId?.let { legById[it]?.carrierPartnerId }
                ?: cost.servicePartnerId?.takeIf { id -> aggregate.legs.any { it.carrierPartnerId == id } }
            if (carrierId != null) {
                val acc = out.getOrPut(carrierId) { CarrierAccumulator(carrierId, carrierNameById[carrierId]) }
                acc.scopedCost += cost.baseCurrencyAmount
            }
        }
    }

    private fun routeSummary(aggregate: LogisticsShipmentAggregate, nowMillis: Long): LogisticsRouteSummaryReadModel {
        val milestones = aggregate.milestones.associateBy { it.id }
        val plannedStopCount = aggregate.milestones.count { it.planKind == LogisticsPlanKind.PLANNED }
        val actualStops = aggregate.milestones.count { it.arrivedAt != null || it.departedAt != null }
        val unplannedStops = aggregate.milestones.count {
            it.planKind == LogisticsPlanKind.UNPLANNED && (it.arrivedAt != null || it.departedAt != null)
        }
        val actualStarts = buildList {
            aggregate.shipment.startedAt?.let(::add)
            aggregate.legs.mapNotNullTo(this) { it.actualDepartureAt }
            aggregate.milestones.mapNotNullTo(this) { it.arrivedAt }
        }
        val actualEnds = buildList {
            aggregate.legs.mapNotNullTo(this) { it.actualArrivalAt }
            aggregate.milestones.mapNotNullTo(this) { it.departedAt ?: it.arrivedAt }
            aggregate.receivingBatches.mapTo(this) { it.receivedAt }
        }
        val start = actualStarts.minOrNull()
        val end = actualEnds.maxOrNull() ?: start?.let { nowMillis }
        val totalCost = aggregate.actualCosts().fold(BigDecimal.ZERO) { sum, cost -> sum + cost.baseCurrencyAmount }
        val crossBorderCost = aggregate.actualCosts().fold(BigDecimal.ZERO) { sum, cost ->
            val leg = cost.legId?.let { aggregate.legs.firstOrNull { leg -> leg.id == it } }
            val from = leg?.let { milestones[it.fromMilestoneId] }
            val to = leg?.let { milestones[it.toMilestoneId] }
            if (from != null && to != null && from.countryCode.isNotBlank() && to.countryCode.isNotBlank() && from.countryCode != to.countryCode) {
                sum + cost.baseCurrencyAmount
            } else sum
        }
        return LogisticsRouteSummaryReadModel(
            shipmentId = aggregate.shipment.id,
            shipmentNumber = aggregate.shipment.shipmentNumber,
            stops = LogisticsRouteStopsReadModel(
                planned = plannedStopCount,
                actual = actualStops,
                unplanned = unplannedStops,
            ),
            totalElapsedMillis = if (start != null && end != null) elapsed(start, end) else 0L,
            totalCost = totalCost,
            crossBorderCost = crossBorderCost,
        )
    }

    private fun countryForCost(
        cost: LogisticsCost,
        aggregate: LogisticsShipmentAggregate,
        milestones: Map<String, LogisticsMilestone>,
    ): LogisticsMilestone? {
        cost.milestoneId?.let { return milestones[it] }
        val leg = cost.legId?.let { id -> aggregate.legs.firstOrNull { it.id == id } } ?: return null
        val from = milestones[leg.fromMilestoneId] ?: return null
        val to = milestones[leg.toMilestoneId] ?: return null
        return from.takeIf { it.countryCode.isNotBlank() && it.countryCode == to.countryCode }
    }

    private fun MutableMap<String, CountryAccumulator>.country(milestone: LogisticsMilestone): CountryAccumulator =
        getOrPut(milestone.countryCode) {
            CountryAccumulator(milestone.countryCode, milestone.countryNameSnapshot.ifBlank { milestone.countryCode })
        }


    private fun LogisticsShipmentAggregate.actualCosts(): List<LogisticsCost> =
        costs.filter { it.status == LogisticsCostStatus.ACTUAL }

    private fun elapsed(start: Long, end: Long): Long = (end - start).coerceAtLeast(0L)

    private fun classify(elapsedMillis: Long, expectedDays: Int?): LogisticsDelayLevel? {
        val expected = expectedDays?.takeIf { it > 0 }?.let(::daysToMillis) ?: return null
        return when {
            elapsedMillis <= expected -> LogisticsDelayLevel.NORMAL
            elapsedMillis <= saturatedDouble(expected) -> LogisticsDelayLevel.LATE
            else -> LogisticsDelayLevel.VERY_LATE
        }
    }

    private fun daysToMillis(days: Int): Long {
        val value = days.toLong()
        return if (value > Long.MAX_VALUE / DAY_MILLIS) Long.MAX_VALUE else value * DAY_MILLIS
    }

    private fun saturatedDouble(value: Long): Long = if (value > Long.MAX_VALUE / 2L) Long.MAX_VALUE else value * 2L

    private class CountryAccumulator(
        val code: String,
        val name: String,
    ) {
        var movementCount: Int = 0
        var transitMillis: Long = 0L
        var dwellMillis: Long = 0L
        var scopedCost: BigDecimal = BigDecimal.ZERO
        var late: Int = 0
        var veryLate: Int = 0
        fun recordDelay(level: LogisticsDelayLevel) {
            when (level) {
                LogisticsDelayLevel.NORMAL -> Unit
                LogisticsDelayLevel.LATE -> late += 1
                LogisticsDelayLevel.VERY_LATE -> veryLate += 1
            }
        }

        fun toReadModel() = LogisticsCountrySummaryReadModel(
            countryCode = code,
            countryName = name,
            movement = LogisticsCountryMovementReadModel(movementCount, transitMillis, dwellMillis),
            scopedCost = scopedCost,
            delays = LogisticsDelayCountsReadModel(late = late, veryLate = veryLate),
        )
    }

    private class CarrierAccumulator(
        val id: String,
        val name: String?,
    ) {
        var handledLegs: Int = 0
        var totalTransitMillis: Long = 0L
        var normal: Int = 0
        var late: Int = 0
        var veryLate: Int = 0
        var scopedCost: BigDecimal = BigDecimal.ZERO
        fun toReadModel() = LogisticsCarrierSummaryReadModel(
            carrierId = id,
            carrierName = name,
            movement = LogisticsCarrierMovementReadModel(
                handledLegCount = handledLegs,
                averageActualTransitMillis = if (handledLegs == 0) 0L else totalTransitMillis / handledLegs,
            ),
            delays = LogisticsDelayCountsReadModel(normal, late, veryLate),
            scopedCost = scopedCost,
        )
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
