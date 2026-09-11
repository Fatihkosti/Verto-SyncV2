package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import kotlinx.serialization.json.Json

/** Atomic persistence boundary for customs lifecycle and the broker custody transition. */
internal class LogisticsCustomsStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    suspend fun saveCustomsTransition(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        handoff: LogisticsCustodyHandoff?,
        event: LogisticsEvent,
    ) {
        require(shipment.organizationId == event.organizationId) { "Customs event tenant mismatch" }
        require(shipment.id == milestone.shipmentId && shipment.id == event.shipmentId) {
            "Customs transition belongs to another shipment"
        }
        handoff?.let {
            require(it.organizationId == shipment.organizationId) { "Customs handoff tenant mismatch" }
            require(it.shipmentId == shipment.id && it.milestoneId == milestone.id) {
                "Customs handoff belongs to another shipment/milestone"
            }
        }
        database.withTransaction {
            require(dao.getShipment(shipment.organizationId, shipment.id) != null) { "Shipment not found" }
            require(dao.getMilestones(shipment.organizationId, shipment.id).any { it.id == milestone.id }) {
                "Customs milestone not found"
            }
        handoff?.let { persistHandoffPartnerChecks(shipment.organizationId, it) }
            handoff?.let { dao.insertCustodyHandoff(it.toEntityV2()) }
            require(dao.updateMilestone(milestone.toEntityV2(shipment.organizationId)) == 1) {
                "Customs milestone update failed"
            }
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    private suspend fun persistHandoffPartnerChecks(organizationId: String, handoff: LogisticsCustodyHandoff) {
        if (handoff.fromHolderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
            val fromHolderId = handoff.fromHolderId
            require(fromHolderId != null && dao.getPartner(organizationId, fromHolderId) != null) {
                "Customs handoff source partner not found"
            }
        }
        if (handoff.toHolderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
            val toHolderId = handoff.toHolderId
            require(toHolderId != null && dao.getPartner(organizationId, toHolderId) != null) {
                "Customs handoff destination partner not found"
            }
        }
    }
}
