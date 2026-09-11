package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CashReconciliationDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("employee_id") val employeeId: String = "",
    @SerialName("employee_name") val employeeName: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("opening_balance") val openingBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_sales") val totalSales: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_refunds") val totalRefunds: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_cash_in") val totalCashIn: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_cash_out") val totalCashOut: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("expected_balance") val expectedBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("actual_counted_balance") val actualCountedBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val variance: BigDecimal = BigDecimal.ZERO,
    @SerialName("variance_reason") val varianceReason: String = "",
    val status: String = "OPEN",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val notes: String = ""
)

// ── فئة نقدية في الجرد (SYNC-014.c) ───────────────────────────
@Serializable
data class CashDenominationDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("reconciliation_id") val reconciliationId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("denomination_value") val denominationValue: BigDecimal = BigDecimal.ZERO,
    val count: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val subtotal: BigDecimal = BigDecimal.ZERO,
    @SerialName("is_coin") val isCoin: Boolean = false
)

// ── الميزانية (SYNC-014.b) ────────────────────────────────────
@Serializable
data class BudgetDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("period_type") val periodType: String = "MONTHLY",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("budget_type") val budgetType: String = "SALES_TARGET",
    val category: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("target_amount") val targetAmount: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ── تخصيص التكلفة (SYNC-014.b) ────────────────────────────────
@Serializable
data class CostAllocationDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("item_id") val itemId: String = "",
    @SerialName("source_type") val sourceType: String = "SHIPMENT_COST",
    @SerialName("source_id") val sourceId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("allocated_amount") val allocatedAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("per_unit_cost") val perUnitCost: BigDecimal = BigDecimal.ZERO,
    @SerialName("quantity_affected") val quantityAffected: Int = 0,
    val method: String = "BY_QUANTITY",
    val note: String = ""
)

// ── وحدة المخزون (SYNC-014.a) ─────────────────────────────────
