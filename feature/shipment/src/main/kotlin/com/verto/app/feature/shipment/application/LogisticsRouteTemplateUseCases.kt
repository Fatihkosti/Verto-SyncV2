package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class SaveLogisticsRouteTemplateUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(organizationId: String, template: LogisticsRouteTemplate): LogisticsRouteTemplate {
        require(organizationId.isNotBlank() && template.organizationId == organizationId) { "Route template organization mismatch" }
        require(template.name.isNotBlank()) { "Route template name is required" }
        require(template.originCountryCode.isNotBlank() && template.originCity.isNotBlank() && template.destinationCountryCode.isNotBlank() && template.destinationCity.isNotBlank()) { "Route endpoints are required" }
        require(template.stops.size >= 2) { "Route template requires at least two stops" }
        require(template.stops.sortedBy { it.order }.map { it.order } == template.stops.indices.toList()) { "Route template stop order must be contiguous" }
        require(template.customsStopOrder == null || template.customsStopOrder in 1 until template.stops.lastIndex) {
            "Customs must be an intermediate station or absent"
        }
        when (template.transportPlanKind) {
            LogisticsRouteTransportPlanKind.UNIFIED -> require(template.unifiedTransportMode in setOf(
                LogisticsLegTransportMode.ROAD, LogisticsLegTransportMode.SEA, LogisticsLegTransportMode.AIR,
            )) { "Unified route requires ROAD, SEA or AIR" }
            LogisticsRouteTransportPlanKind.MIXED -> require(template.unifiedTransportMode == null) { "Mixed route does not persist a unified mode" }
        }
        template.stops.dropLast(1).forEach { require((it.expectedTransitMinutesToNext ?: 0) > 0) { "Every route leg requires an expected transit duration" } }
        if (template.customsStopOrder == null) {
            require(template.expectedCustomsMinutes == null) { "Customs duration requires a customs station" }
        } else {
            require((template.expectedCustomsMinutes ?: 0) > 0) { "Customs station requires an expected duration" }
        }
        val now = clock.now()
        val templateId = template.id.ifBlank { identities.newId() }
        val persisted = template.copy(
            id = templateId,
            name = template.name.trim(),
            createdAt = template.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
            stops = template.stops.sortedBy { it.order }.mapIndexed { index, stop ->
                stop.copy(
                    id = stop.id.ifBlank { identities.newId() },
                    templateId = templateId,
                    order = index,
                )
            },
        )
        store.upsertRouteTemplate(persisted)
        return persisted
    }
}
