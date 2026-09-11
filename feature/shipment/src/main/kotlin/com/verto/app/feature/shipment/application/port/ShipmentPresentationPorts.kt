package com.verto.app.feature.shipment.application.port

import com.verto.app.feature.shipment.application.model.LogisticsEmployeeRef
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedReadRecord
import com.verto.app.feature.shipment.application.model.ShipmentEventReadRecord
import kotlinx.coroutines.flow.Flow

/** Consumer-owned bridge for foreign-feature reads needed by Logistics planning. */
interface LogisticsPlanningReferencePort {
    suspend fun activeEmployees(): List<LogisticsEmployeeRef>
    suspend fun recentPurchaseInvoiceIds(fromInclusive: Long, toInclusive: Long): List<String>
}

/** V2-only read bridge used by Logistics presentation after the v233 cutover. */
interface LogisticsUnifiedReadPort {
    fun observe(organizationId: String): Flow<List<LogisticsUnifiedReadRecord>>
    suspend fun events(organizationId: String, shipmentId: String): List<ShipmentEventReadRecord> = emptyList()
}
