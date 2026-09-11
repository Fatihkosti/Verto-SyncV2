package com.verto.app.feature.reports.application.model

data class SmartInsight(
    val id: String,
    val icon: String,
    val title: String,
    val message: String,
    val type: InsightType,
    val actionLabel: String? = null,
    val actionTarget: InsightTarget? = null
)

enum class InsightType { WARNING, OPPORTUNITY, ACHIEVEMENT, ANOMALY }

enum class InsightTarget { FINANCIAL, SALES, OPERATIONS, RECEIVABLES, INVENTORY, BUDGET }


data class InsightInput(
    val todaySales: Double = 0.0,
    val avgLast30DaySales: Double = 0.0,
    val periodSales: Double = 0.0,
    val expensesTotal: Double = 0.0,
    val totalCreditAmount: Double = 0.0,
    val overdueAmount: Double = 0.0,
    val deadStockValue: Double = 0.0,
    val topItemName: String = "",
    val topItemGrowthPct: Double = 0.0,
    val budgetTarget: Double = 0.0,
    val atRiskClientsCount: Int = 0,
    val forecastMonthTotal: Double = 0.0
)
