package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CancelLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsV234CancellationPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

/** Session 307: former production delete is now an auditable CANCEL state transition. */
class DeleteLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val cancelShipment: CancelLogisticsShipmentUseCase,
) {
    data class Command(
        val shipmentId: String,
        val confirmationShipmentNumber: String? = null,
        val reason: String = "",
        val requestId: String,
        val actor: LogisticsCashActor,
    )

    suspend operator fun invoke(organizationId: String, command: Command) {
        require(organizationId.isNotBlank() && command.shipmentId.isNotBlank() && command.requestId.isNotBlank()) {
            "Shipment cancel identity is incomplete"
        }
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: return
        require(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(aggregate.shipment)) {
            "Only an unstarted draft shipment can use this production removal action"
        }
        cancelShipment(
            organizationId,
            CancelLogisticsShipmentCommand(
                shipmentId = command.shipmentId,
                cancelledAt = System.currentTimeMillis(),
                requestId = command.requestId,
                reason = command.reason.trim().ifBlank { "USER_REMOVAL_REQUEST" },
            )
        )
    }
}
