package com.verto.app.feature.shipment.data.pendingaction

import com.verto.app.data.local.dao.LogisticsDao
import com.verto.app.feature.shipment.application.pendingaction.ShipmentReceiptIssuePendingActionSource
import com.verto.app.feature.shipment.application.pendingaction.ShipmentReceiptIssueRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomShipmentReceiptIssuePendingActionSource @Inject constructor(
    private val logisticsDao: LogisticsDao,
) : ShipmentReceiptIssuePendingActionSource {
    override fun observeReceiptIssues(
        organizationId: String,
    ): Flow<List<ShipmentReceiptIssueRecord>> =
        logisticsDao.observeHomeReceiptIssues(organizationId).map { rows ->
            rows.map { row ->
                ShipmentReceiptIssueRecord(
                    receiptId = row.receiptId,
                    shipmentId = row.shipmentId,
                    shipmentTitle = row.shipmentNumber.takeIf(String::isNotBlank)?.let { "شحنة #$it" } ?: "شحنة",
                    missingItems = row.remainingMissingQuantity.toString(),
                    damagedItems = "",
                    receivedAtEpochMillis = row.detectedAt,
                )
            }
        }
}
