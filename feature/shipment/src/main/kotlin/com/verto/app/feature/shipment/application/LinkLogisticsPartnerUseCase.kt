package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentPartnerLink
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class LinkLogisticsPartnerUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
        partnerId: String,
        role: LogisticsPartnerRole,
    ): LogisticsShipmentPartnerLink {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(partnerId.isNotBlank()) { "partnerId is required" }
        val aggregate = store.getShipment(organizationId, shipmentId)
            ?: error("Logistics shipment not found")
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED) { "Closed shipment cannot change partners" }
        require(aggregate.shipment.state != LogisticsShipmentState.CANCELLED) { "Cancelled shipment cannot change partners" }
        require(aggregate.partners.none { it.partnerId == partnerId && it.role == role }) {
            "Partner is already linked with this role"
        }
        val link = LogisticsShipmentPartnerLink(
            id = identities.newId(),
            shipmentId = shipmentId,
            partnerId = partnerId,
            role = role,
        )
        store.linkPartner(organizationId, link)
        return link
    }
}
