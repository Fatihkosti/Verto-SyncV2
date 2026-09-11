package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

enum class LogisticsShortageSettlementType { COMPENSATED, FINAL_LOSS }

data class LogisticsShortageSettlementIdentity(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val shortageId: String,
)

data class LogisticsShortageCompensation(
    val amount: BigDecimal,
    val currency: String,
    val exchangeRateSnapshot: BigDecimal,
    val baseCurrencyAmount: BigDecimal,
)

data class LogisticsShortageSettlementAudit(
    val occurredAt: Long,
    val employee: LogisticsAssigneeSnapshot?,
    val note: String,
    val requestId: String,
)

data class LogisticsShortageSettlement(
    val identity: LogisticsShortageSettlementIdentity,
    val type: LogisticsShortageSettlementType,
    val quantity: Int,
    val compensation: LogisticsShortageCompensation? = null,
    val audit: LogisticsShortageSettlementAudit,
) {
    val id get() = identity.id
    val organizationId get() = identity.organizationId
    val shipmentId get() = identity.shipmentId
    val shortageId get() = identity.shortageId
    val compensationAmount get() = compensation?.amount
    val currency get() = compensation?.currency
    val exchangeRateSnapshot get() = compensation?.exchangeRateSnapshot
    val baseCurrencyAmount get() = compensation?.baseCurrencyAmount
    val occurredAt get() = audit.occurredAt
    val employeeId get() = audit.employee?.employeeId
    val employeeNameSnapshot get() = audit.employee?.employeeName
    val note get() = audit.note
    val requestId get() = audit.requestId
}

data class LogisticsLateCostAdjustmentIdentity(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val costId: String,
)

data class LogisticsLateCostAdjustment(
    val identity: LogisticsLateCostAdjustmentIdentity,
    val recordedAt: Long,
    val employee: LogisticsAssigneeSnapshot?,
    val requestId: String,
) {
    val id get() = identity.id
    val organizationId get() = identity.organizationId
    val shipmentId get() = identity.shipmentId
    val costId get() = identity.costId
    val employeeId get() = employee?.employeeId
    val employeeNameSnapshot get() = employee?.employeeName
}

data class LogisticsLateCostAllocation(
    val id: String,
    val organizationId: String,
    val adjustmentId: String,
    val shipmentId: String,
    val shipmentLineId: String,
    val amount: BigDecimal,
)
