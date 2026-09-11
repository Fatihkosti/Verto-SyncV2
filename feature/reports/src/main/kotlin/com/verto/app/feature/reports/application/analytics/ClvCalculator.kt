package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.ReportRfmMetrics

import com.verto.app.utils.MoneyMath

/**
 * ClvCalculator — حساب قيمة العميل طوال فترة علاقته (Customer Lifetime Value)
 *
 * Historic CLV  = مجموع الأرباح الفعلية التاريخية
 * Projected value = متوسط الربح/فاتورة × معدل الشراء السنوي × أفق التنبؤ المستقبلي.
 *
 * لا نستخدم عمر العلاقة التاريخي كأفق تنبؤ؛ فعل ذلك كان يُلغي المعادلة جبرياً ويعيد
 * totalProfit نفسه تقريباً، وهو ليس تنبؤاً.
 */
class ClvCalculator {

    fun historicClv(rfm: ReportRfmMetrics): Double = rfm.totalProfit

    fun projectedClv(rfm: ReportRfmMetrics, projectionYears: Double = 1.0): Double {
        require(projectionYears.isFinite() && projectionYears > 0.0) { "projectionYears must be positive" }
        if (rfm.totalInvoiceCount <= 0) return 0.0
        val avgProfitPerInvoice = MoneyMath.divide(rfm.totalProfit, rfm.totalInvoiceCount.toDouble())
        // Conservative annualization: relationships younger than one year are not extrapolated as 12x monthly behavior.
        val observedYears = (rfm.customerLifespanDays.toDouble() / 365.0).coerceAtLeast(1.0)
        val freqPerYear = rfm.totalInvoiceCount.toDouble() / observedYears
        return MoneyMath.multiply(
            MoneyMath.multiply(avgProfitPerInvoice, freqPerYear),
            projectionYears
        )
    }

    fun topByClv(rfmList: List<ReportRfmMetrics>, limit: Int = 10): List<Pair<ReportRfmMetrics, Double>> =
        rfmList
            .map { it to projectedClv(it) }
            .sortedByDescending { it.second }
            .take(limit)
}
