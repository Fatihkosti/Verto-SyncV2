package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.SaveShipmentRouteCommand
import javax.inject.Inject

/**
 * Semantic application boundary for Screen 3 dynamic route planning.
 * Route topology and execution-history rules remain centralized in [SaveShipmentRouteUseCase].
 */
class SaveDynamicRoutePlanUseCase @Inject constructor(
    private val saveShipmentRoute: SaveShipmentRouteUseCase,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: SaveShipmentRouteCommand,
    ): LogisticsShipment = saveShipmentRoute(organizationId, command)
}
