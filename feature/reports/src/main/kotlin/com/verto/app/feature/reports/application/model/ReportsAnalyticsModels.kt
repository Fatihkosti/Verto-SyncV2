package com.verto.app.feature.reports.application.model

data class DailySales(val dateMs: Long, val dayOfWeek: Int, val amount: Double)

data class ForecastPoint(
    val dateMs: Long,
    val predicted: Double,
    val lower: Double,
    val upper: Double
)

data class ForecastResult(
    val history: List<DailySales>,
    val forecast: List<ForecastPoint>,
    val weekTotal: Double,
    val monthTotal: Double
)

/** F250: historical realized margin is distinct from today's replacement margin. */
data class ItemRealMargin(
    val itemId: String,
    val itemName: String,
    val revenue: Double,
    val qty: Int,
    val realizedMarginPct: Float,
    val replacementMarginPct: Float,
    val replacementCostPerUnit: Double,
    val historicalCostComplete: Boolean,
)

data class ForecastInvoice(
    val createdAt: Long,
    val totalAmount: Double
)

data class ReportInvoiceLine(
    val inventoryItemId: String,
    val itemName: String,
    val revenueMinor: Long,
    val costAtSaleMinor: Long,
    val currentReplacementUnitCostMinor: Long,
    val quantity: Int,
    val costSnapshotKnown: Boolean,
    val currencyCode: String,
)
