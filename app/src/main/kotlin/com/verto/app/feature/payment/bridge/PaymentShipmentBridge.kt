package com.verto.app.feature.payment.bridge

import android.content.Context
import com.verto.app.R
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.feature.payment.application.model.PurchaseShipmentCreateCommand
import com.verto.app.feature.payment.application.model.PurchaseShipmentOption
import com.verto.app.feature.payment.application.port.PurchaseShipmentGateway
import com.verto.app.feature.shipment.application.CreateLogisticsShipmentUseCase
import com.verto.app.feature.shipment.domain.model.CreateLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/** Payment-owned bridge now targets Logistics V2 only; no legacy shipment persistence is reachable. */
class PaymentShipmentBridge @Inject constructor(
    private val logisticsStore: LogisticsShipmentStorePort,
    private val createLogisticsShipment: CreateLogisticsShipmentUseCase,
    private val identities: LogisticsIdentityPort,
    private val sessionReader: SessionReader,
    private val permissionProvider: PermissionProvider,
    @ApplicationContext private val appContext: Context,
) : PurchaseShipmentGateway {

    override fun observeActiveShipments(): Flow<List<PurchaseShipmentOption>> =
        sessionReader.organizationId.flatMapLatest { organizationId ->
            logisticsStore.observeShipments(organizationId.trim()).map { shipments ->
                shipments
                    .filter { it.state != LogisticsShipmentState.CLOSED && it.state != LogisticsShipmentState.CANCELLED }
                    .map { shipment ->
                        PurchaseShipmentOption(
                            id = shipment.id,
                            title = shipment.shipmentNumber.takeIf(String::isNotBlank)?.let {
                                appContext.getString(R.string.legacy_ui_62f8303c6a05, it)
                            } ?: appContext.getString(
                                R.string.legacy_ui_86a060fbf899,
                                shipment.sourceLocation,
                                shipment.destinationLocation,
                            ),
                            statusLabel = shipment.state.name,
                        )
                    }
            }
        }

    override suspend fun createShipment(command: PurchaseShipmentCreateCommand) {
        if (!permissionProvider.canNow { it.shipmentsManage }) return
        val organizationId = sessionReader.snapshot().organization.id.trim()
        require(organizationId.isNotBlank()) { "Organization is unavailable" }
        createLogisticsShipment(
            CreateLogisticsShipmentCommand(
                organizationId = organizationId,
                shipmentNumber = "AUTO",
                sourceLocation = command.origin,
                destinationLocation = command.destination,
                createdAt = command.createdAt,
                requestId = "payment-create:${identities.newId()}",
                notes = command.routeStops.joinToString(" → ").takeIf(String::isNotBlank).orEmpty(),
            ),
        )
    }
}
