package com.verto.app.feature.shipment.bridge

import com.verto.app.data.local.dao.LogisticsDao
import com.verto.app.feature.reports.application.port.ReportsLogisticsSource
import com.verto.app.feature.reports.application.port.ReportsShipmentCostSnapshot
import com.verto.app.feature.reports.application.port.ReportsShipmentSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ShipmentReportsLogisticsSourceAdapter @Inject constructor(
    private val logisticsDao: LogisticsDao,
) : ReportsLogisticsSource {
    override fun observeShipments(organizationId: String): Flow<List<ReportsShipmentSnapshot>> =
        logisticsDao.observeShipments(organizationId).map { rows ->
            rows.map { row -> ReportsShipmentSnapshot(row.id, row.shipmentNumber, row.state, row.startedAt) }
        }

    override fun observeCosts(organizationId: String): Flow<List<ReportsShipmentCostSnapshot>> =
        logisticsDao.observeCosts(organizationId).map { rows ->
            rows.map { row -> ReportsShipmentCostSnapshot(row.shipmentId, row.status, row.baseCurrencyAmount) }
        }
}
