package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class UpsertLogisticsPartnerUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
) {
    suspend operator fun invoke(partner: LogisticsPartner): LogisticsPartner {
        require(partner.id.isNotBlank()) { "partner.id is required" }
        require(partner.organizationId.isNotBlank()) { "partner.organizationId is required" }
        require(partner.name.isNotBlank()) { "partner.name is required" }
        val normalized = partner.copy(
            id = partner.id.trim(),
            organizationId = partner.organizationId.trim(),
            name = partner.name.trim(),
            phone = partner.phone?.trim()?.takeIf(String::isNotBlank),
            representativeName = partner.representativeName?.trim()?.takeIf(String::isNotBlank),
            representativePhone = partner.representativePhone?.trim()?.takeIf(String::isNotBlank),
            notes = partner.notes.trim(),
        )
        store.upsertPartner(normalized)
        return normalized
    }
}
