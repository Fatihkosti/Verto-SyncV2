package com.verto.app.feature.reports.application.model

/** Export contracts carry fixed-point money and explicit currency. */
data class ReportItemExportRow(
    val name: String,
    val quantity: Int,
    val revenueMinor: Long,
    val profitMinor: Long,
    val currencyCode: String,
)

data class ReportCategoryExportRow(
    val category: String,
    val distinctItems: Int,
    val revenueMinor: Long,
    val profitMinor: Long,
    val currencyCode: String,
)
