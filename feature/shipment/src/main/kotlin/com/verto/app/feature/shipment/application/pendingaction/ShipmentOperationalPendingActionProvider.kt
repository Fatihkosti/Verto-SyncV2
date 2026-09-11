package com.verto.app.feature.shipment.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.shipment.application.EvaluateLogisticsDelayUseCase
import com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionEventKey
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

data class ShipmentOperationalRecord(
    val shipmentId: String,
    val shipmentTitle: String,
    val state: LogisticsShipmentState,
    val createdAtEpochMillis: Long,
    val expectedArrivalDateEpochMillis: Long?,
    val delaySnapshot: LogisticsShipmentAggregate,
)

interface ShipmentOperationalPendingActionSource {
    fun observeOperationalShipments(organizationId: String): Flow<List<ShipmentOperationalRecord>>
}

interface ShipmentOperationalPendingActionClock {
    fun observeNowEpochMillis(): Flow<Long>
}

class ShipmentOperationalPendingActionProvider @Inject constructor(
    private val source: ShipmentOperationalPendingActionSource,
    private val clock: ShipmentOperationalPendingActionClock,
    private val sessionReader: SessionReader,
    private val evaluateDelay: EvaluateLogisticsDelayUseCase? = null,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            !context.allows(HomePermissionKeys.SHIPMENTS_VIEW)
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(combine(source.observeOperationalShipments(context.organizationId), clock.observeNowEpochMillis()) { rows, now ->
            buildList {
                rows.forEach { shipment ->
                    val delay = runCatching {
                        evaluateDelay?.evaluate(shipment.delaySnapshot, now)
                    }.getOrNull()
                    if (delay?.level == LogisticsDelayLevel.VERY_LATE) add(shipment.veryLateEvent(delay))
                    if (shipment.state == LogisticsShipmentState.CUSTOMS) add(shipment.customsEvent())
                    if (shipment.state == LogisticsShipmentState.RECEIVING) add(shipment.arrivalEvent())
                }
            }
                .distinctBy { it.eventKey }
                .sortedWith(PROVIDER_RANKING)
        })
    }

    private fun ShipmentOperationalRecord.veryLateEvent(delay: com.verto.app.feature.shipment.application.model.LogisticsDelayReadModel): PendingAction {
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_VERY_LATE, shipmentId)
        val contact = delay.contact?.displayName?.let { " • تواصل مع $it" }.orEmpty()
        return PendingAction(
            eventKey = eventKey,
            title = "شحنة تحتاج متابعة عاجلة",
            summary = "$shipmentTitle • تأخير ${delay.timing.delayDays} يوم • ${delay.currentStage}$contact",
            occurredAtEpochMillis = delay.timing.veryLateThresholdAt?.coerceAtLeast(0L) ?: createdAtEpochMillis.coerceAtLeast(0L),
            priority = PendingActionPriority.HIGH,
            destination = shipmentDestination(),
            actions = standardActions(eventKey),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        )
    }

    private fun ShipmentOperationalRecord.customsEvent(): PendingAction {
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_AT_CUSTOMS, shipmentId)
        return PendingAction(
            eventKey = eventKey,
            title = "شحنة وصلت الجمارك",
            summary = shipmentTitle,
            occurredAtEpochMillis = createdAtEpochMillis.coerceAtLeast(0L),
            priority = PendingActionPriority.HIGH,
            destination = shipmentDestination(),
            actions = standardActions(eventKey),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        )
    }

    private fun ShipmentOperationalRecord.arrivalEvent(): PendingAction {
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_GOODS_ARRIVED, shipmentId)
        return PendingAction(
            eventKey = eventKey,
            title = "بضاعة دولية وصلت",
            summary = "$shipmentTitle • جاهزة للاستلام",
            occurredAtEpochMillis = createdAtEpochMillis.coerceAtLeast(0L),
            priority = PendingActionPriority.NORMAL,
            destination = shipmentDestination(),
            actions = standardActions(eventKey),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        )
    }

    private fun ShipmentOperationalRecord.shipmentDestination() = HomeDestination(
        id = HomeDestinationIds.SHIPMENT_DETAILS,
        arguments = mapOf("shipmentId" to shipmentId),
    )

    private fun ShipmentOperationalRecord.standardActions(eventKey: String): List<HomeAction> = listOf(
        HomeAction(
            id = "open_shipment",
            label = "فتح",
            destination = shipmentDestination(),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        ),
        HomeAction(
            id = "remind_later",
            label = "تذكير",
            destination = HomeDestination(HomeDestinationIds.PENDING_ACTION_REMIND, arguments = mapOf("eventKey" to eventKey)),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        ),
    )

    private val PROVIDER_RANKING: Comparator<PendingAction> =
        compareByDescending<PendingAction> { action -> action.priority.localRank }
            .thenBy { action -> action.occurredAtEpochMillis }
            .thenBy { action -> action.eventKey }

    private val PendingActionPriority.localRank: Int
        get() = when (this) {
            PendingActionPriority.LOW -> 0
            PendingActionPriority.NORMAL -> 1
            PendingActionPriority.HIGH -> 2
            PendingActionPriority.CRITICAL -> 3
        }

    companion object {
        const val PROVIDER_ID = "shipment.operational"
        const val EVENT_VERY_LATE = "very_late_v2"
        const val EVENT_AT_CUSTOMS = "at_customs_v2"
        const val EVENT_GOODS_ARRIVED = "goods_arrived_v2"
    }
}
