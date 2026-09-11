package com.verto.app.feature.reports.application.port

import kotlinx.coroutines.flow.Flow

data class ReportsShipmentSnapshot(
    val id: String,
    val shipmentNumber: String,
    val state: String,
    val startedAt: Long?,
)

data class ReportsShipmentCostSnapshot(
    val shipmentId: String,
    val status: String,
    val baseCurrencyAmount: String,
)

/** Reports-owned projection over Shipment's stable domain store. No Room type crosses the contract. */
interface ReportsLogisticsSource {
    fun observeShipments(organizationId: String): Flow<List<ReportsShipmentSnapshot>>
    fun observeCosts(organizationId: String): Flow<List<ReportsShipmentCostSnapshot>>
}
