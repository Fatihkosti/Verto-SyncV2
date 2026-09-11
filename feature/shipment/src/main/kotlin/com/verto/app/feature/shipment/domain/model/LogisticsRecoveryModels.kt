package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

enum class LogisticsShortageStatus { OPEN, PARTIALLY_RECOVERED, RECOVERED }

data class LogisticsShortageIdentity(
    val organizationId: String,
    val id: String,
    val shipmentId: String,
    val shipmentLineId: String,
)

data class LogisticsShortageQuantity(
    val originalMissingQuantity: Int,
    val remainingMissingQuantity: Int,
    val basePurchaseUnitPriceSnapshot: BigDecimal,
)

data class LogisticsShortage(
    val identity: LogisticsShortageIdentity,
    val quantity: LogisticsShortageQuantity,
    val status: LogisticsShortageStatus,
    val detectedAt: Long,
    val note: String = "",
    val requestId: String,
)

data class LogisticsRecovery(
    val organizationId: String,
    val id: String,
    val shipmentId: String,
    val recoveredAt: Long,
    val employee: LogisticsAssigneeSnapshot,
    val note: String = "",
    val requestId: String
)

data class LogisticsRecoveryEconomics(
    val basePurchaseUnitPriceSnapshot: BigDecimal,
    val allocatedRecoveryCost: BigDecimal = BigDecimal.ZERO,
)

data class LogisticsRecoveryLine(
    val organizationId: String,
    val id: String,
    val recoveryId: String,
    val shortageId: String,
    val shipmentLineId: String,
    val recoveredQuantity: Int,
    val economics: LogisticsRecoveryEconomics
)

data class LogisticsRecoveryPosting(
    val organizationId: String,
    val postingId: String,
    val recoveryId: String,
    val recoveryLineId: String,
    val shipmentId: String,
    val shipmentLineId: String,
    val quantity: Int
)
