package com.verto.app.feature.shipment.bridge

import com.verto.app.data.local.dao.LogisticsDao
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedReadRecord
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedSource
import com.verto.app.feature.shipment.application.model.ShipmentEventReadRecord
import com.verto.app.feature.shipment.application.port.LogisticsUnifiedReadPort
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** V2-only read adapter used by the logistics center and completed-shipment audit detail. */
class LogisticsUnifiedReadModelAdapter @Inject constructor(
    private val logisticsV2: LogisticsShipmentStorePort,
    private val logisticsDao: LogisticsDao,
) : LogisticsUnifiedReadPort {
    override fun observe(organizationId: String): Flow<List<LogisticsUnifiedReadRecord>> =
        logisticsV2.observeShipments(organizationId).map { rows -> rows.map { it.asUnifiedReadRecord() } }

    override suspend fun events(organizationId: String, shipmentId: String): List<ShipmentEventReadRecord> =
        logisticsDao.getEvents(organizationId, shipmentId).map { event ->
            ShipmentEventReadRecord(
                id = event.id,
                type = event.type,
                occurredAt = event.occurredAt,
                employeeName = event.employeeNameSnapshot,
                payloadJson = event.payloadJson,
            )
        }

    private fun LogisticsShipment.asUnifiedReadRecord(): LogisticsUnifiedReadRecord =
        LogisticsUnifiedReadRecord(
            source = LogisticsUnifiedSource.V2,
            id = id,
            shipmentNumber = shipmentNumber,
            sourceLocation = sourceLocation,
            destinationLocation = destinationLocation,
            createdAt = createdAt,
            expectedArrivalAt = expectedArrivalAt,
            v2Shipment = this,
        )
}
