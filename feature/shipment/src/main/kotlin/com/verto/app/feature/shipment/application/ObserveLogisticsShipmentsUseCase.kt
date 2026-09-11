package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLogisticsShipmentsUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
) {
    operator fun invoke(organizationId: String): Flow<List<LogisticsShipment>> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        return store.observeShipments(organizationId)
    }
}
