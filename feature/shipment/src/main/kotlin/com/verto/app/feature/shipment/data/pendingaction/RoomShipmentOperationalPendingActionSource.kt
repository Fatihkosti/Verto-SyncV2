package com.verto.app.feature.shipment.data.pendingaction

import com.verto.app.data.local.dao.HomeOperationalCustomsPlanRow
import com.verto.app.data.local.dao.HomeOperationalLegRow
import com.verto.app.data.local.dao.HomeOperationalMilestoneRow
import com.verto.app.data.local.dao.HomeOperationalShipmentRow
import com.verto.app.data.local.dao.LogisticsDao
import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalPendingActionSource
import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalRecord
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Home-specific logistics read model.
 *
 * It batches four lightweight Room projections once per database emission and constructs only the
 * delay-policy inputs. No shipment documents, costs, payments, recoveries, receiving history, or
 * partner lists are hydrated.
 */
class RoomShipmentOperationalPendingActionSource @Inject constructor(
    private val logisticsDao: LogisticsDao,
) : ShipmentOperationalPendingActionSource {
    override fun observeOperationalShipments(
        organizationId: String,
    ): Flow<List<ShipmentOperationalRecord>> = combine(
        logisticsDao.observeHomeOperationalShipments(organizationId),
        logisticsDao.observeHomeOperationalMilestones(organizationId),
        logisticsDao.observeHomeOperationalLegs(organizationId),
        logisticsDao.observeHomeOperationalCustomsPlans(organizationId),
    ) { shipments, milestones, legs, customsPlans ->
        val milestonesByShipment = milestones.groupBy(HomeOperationalMilestoneRow::shipmentId)
        val legsByShipment = legs.groupBy(HomeOperationalLegRow::shipmentId)
        val customsByShipment = customsPlans.associateBy(HomeOperationalCustomsPlanRow::shipmentId)

        shipments.mapNotNull { shipment ->
            shipment.toPendingRecord(
                milestones = milestonesByShipment[shipment.shipmentId].orEmpty(),
                legs = legsByShipment[shipment.shipmentId].orEmpty(),
                customsPlan = customsByShipment[shipment.shipmentId],
            )
        }
    }

    private fun HomeOperationalShipmentRow.toPendingRecord(
        milestones: List<HomeOperationalMilestoneRow>,
        legs: List<HomeOperationalLegRow>,
        customsPlan: HomeOperationalCustomsPlanRow?,
    ): ShipmentOperationalRecord? {
        val shipmentState = enumOrNull<LogisticsShipmentState>(state) ?: return null
        val domainShipment = LogisticsShipment(
            id = shipmentId,
            organizationId = organizationId,
            shipmentNumber = shipmentNumber,
            sourceLocation = sourceLocation,
            destinationLocation = destinationLocation,
            state = shipmentState,
            createdAt = createdAt,
            startedAt = startedAt,
            expectedArrivalAt = expectedArrivalAt,
            customsMilestoneId = customsMilestoneId,
            customsCalendarPolicyId = customsCalendarPolicyId,
            eventTimezoneId = eventTimezoneId,
        )
        val delaySnapshot = LogisticsShipmentAggregate(
            shipment = domainShipment,
            milestones = milestones.mapNotNull { row -> row.toDomain() },
            legs = legs.mapNotNull { row -> row.toDomain() },
            customsPlan = customsPlan?.toDomain(),
        )
        return ShipmentOperationalRecord(
            shipmentId = shipmentId,
            shipmentTitle = shipmentNumber.takeIf(String::isNotBlank)?.let { "شحنة #$it" }
                ?: "شحنة $sourceLocation → $destinationLocation",
            state = shipmentState,
            createdAtEpochMillis = createdAt,
            expectedArrivalDateEpochMillis = expectedArrivalAt,
            delaySnapshot = delaySnapshot,
        )
    }

    private fun HomeOperationalMilestoneRow.toDomain(): LogisticsMilestone? {
        val milestoneType = enumOrNull<LogisticsMilestoneType>(type) ?: return null
        return LogisticsMilestone(
            id = milestoneId,
            shipmentId = shipmentId,
            type = milestoneType,
            order = milestoneOrder,
            location = location,
            arrivedAt = arrivedAt,
            departedAt = departedAt,
            placeName = placeName.ifBlank { location },
            expectedStayDays = expectedStayDays,
            customsBrokerPartnerId = customsBrokerPartnerId,
            customsBrokerNameSnapshot = customsBrokerNameSnapshot,
            customsBrokerPhoneSnapshot = customsBrokerPhoneSnapshot,
            customsStartedAt = customsStartedAt,
            customsCompletedAt = customsCompletedAt,
        )
    }

    private fun HomeOperationalLegRow.toDomain(): LogisticsShipmentLeg? {
        val legMode = enumOrNull<LogisticsLegTransportMode>(mode) ?: return null
        val legStatus = enumOrNull<LogisticsLegStatus>(status) ?: return null
        return LogisticsShipmentLeg(
            id = legId,
            organizationId = organizationId,
            shipmentId = shipmentId,
            sequence = sequence,
            fromMilestoneId = fromMilestoneId,
            toMilestoneId = toMilestoneId,
            mode = legMode,
            carrierPartnerId = carrierPartnerId.orEmpty(),
            status = legStatus,
            actualDepartureAt = actualDepartureAt,
            actualArrivalAt = actualArrivalAt,
            expectedTransitDays = expectedTransitDays,
            representativeNameSnapshot = representativeNameSnapshot,
            representativePhoneSnapshot = representativePhoneSnapshot,
            supersededAt = supersededAt,
            expectedTransitMinutes = expectedTransitMinutes,
        )
    }

    private fun HomeOperationalCustomsPlanRow.toDomain() = LogisticsCustomsPlan(
        id = planId,
        organizationId = organizationId,
        shipmentId = shipmentId,
        checkpointName = checkpointName,
        afterStationId = afterStationId,
        expectedDurationMinutes = expectedDurationMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        runCatching { enumValueOf<T>(value) }.getOrNull()
}
