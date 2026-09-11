package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.StartLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

/** Starts the journey without claiming physical movement. */
class StartLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(organizationId: String, command: StartLogisticsShipmentCommand): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.startedAt >= 0L) { "startedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment

        LogisticsValidation.validateStart(aggregate)
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.WAITING_DEPARTURE)
        val firstLeg = aggregate.legs.single { it.sequence == 0 }
        require(firstLeg.status == LogisticsLegStatus.PLANNED && firstLeg.actualDepartureAt == null) {
            "First leg must remain planned until movement starts"
        }
        aggregate.legs.forEach { leg ->
            val partner = store.getPartner(organizationId, leg.carrierPartnerId)
                ?: error("Route carrier partner not found")
            require(partner.role == LogisticsPartnerRole.CARRIER || partner.role == LogisticsPartnerRole.FREIGHT_FORWARDER) {
                "Leg carrier must be CARRIER or FREIGHT_FORWARDER"
            }
        }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
        val notReady = aggregate.sources.filter { source ->
            val current = positions.getValue(source.id)
            current.holderType != LogisticsCustodyHolderType.LOGISTICS_PARTNER || current.holderId != firstLeg.carrierPartnerId
        }
        require(notReady.isEmpty()) {
            val invoices = notReady.joinToString { it.invoiceNumberSnapshot }
            "Cannot start shipment: purchase sources are not under first carrier custody: $invoices"
        }

        val started = aggregate.shipment.copy(
            state = LogisticsShipmentState.WAITING_DEPARTURE,
            startedAt = command.startedAt,
        )
        val recordedAt = clock.now()
        store.saveShipmentState(
            started,
            LogisticsEvent(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = started.id,
                type = LogisticsEventType.SHIPMENT_STARTED,
                occurredAt = command.startedAt,
                recordedAt = recordedAt,
                employeeId = started.assignee?.employeeId,
                employeeNameSnapshot = started.assignee?.employeeName,
                requestId = command.requestId,
                payload = mapOf("firstLegId" to firstLeg.id, "movementStarted" to "false"),
            ),
        )
        return started
    }
}
