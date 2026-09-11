package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "budgets",
    indices = [
        Index("periodStart"),
        Index("periodEnd"),
        Index("budgetType")
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val periodStart: Long,
    val periodEnd: Long,

    val budgetType: BudgetType = BudgetType.SALES_TARGET,

    val category: String = "",

    val targetAmount: Double,
    val note: String = "",

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
