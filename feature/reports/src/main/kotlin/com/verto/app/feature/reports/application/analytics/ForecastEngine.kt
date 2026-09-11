package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.DailySales
import com.verto.app.feature.reports.application.model.ForecastInvoice
import com.verto.app.feature.reports.application.model.ForecastPoint
import com.verto.app.feature.reports.application.model.ForecastResult

import com.verto.app.utils.MoneyMath
import com.verto.app.utils.MoneyMath.moneySum
import java.util.Calendar
import kotlin.math.sqrt

/**
 * ForecastEngine — يتنبأ بالمبيعات بـ Exponential Smoothing + تعديل موسمي يومي
 *
 * الخوارزمية:
 * 1. تجميع المبيعات يومياً من الفواتير التاريخية
 * 2. تطبيق Exponential Smoothing (α=0.3) للحصول على baseline
 * 3. حساب معاملات موسمية لكل يوم من الأسبوع
 * 4. حساب نطاق الثقة بناءً على الانحراف المعياري لآخر 14 يوم
 */
class ForecastEngine {

    private val alpha = 0.3

    fun compute(invoices: List<ForecastInvoice>, daysAhead: Int = 30): ForecastResult {
        val history = aggregateByDay(invoices)
        if (history.size < 7) {
            return ForecastResult(history, emptyList(), 0.0, 0.0)
        }

        val values = history.map { it.amount }
        val smoothed = exponentialSmoothing(values)
        val baseValue = smoothed.last()
        val dayFactors = computeDayFactors(history)
        val stdDev = stdDev(values.takeLast(14))

        val lastDate = history.last().dateMs
        val cal = Calendar.getInstance()
        val forecastPts = (1..daysAhead).map { offset ->
            val targetMs = lastDate + offset * 86_400_000L
            cal.timeInMillis = targetMs
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            val factor = dayFactors[dow] ?: 1.0
            val predicted = MoneyMath.multiply(baseValue, factor).coerceAtLeast(0.0)
            ForecastPoint(
                dateMs    = targetMs,
                predicted = predicted,
                lower     = (predicted - stdDev * 1.5).coerceAtLeast(0.0),
                upper     = predicted + stdDev * 1.5
            )
        }

        return ForecastResult(
            history   = history,
            forecast  = forecastPts,
            weekTotal = forecastPts.take(7).map { it.predicted }.moneySum(),
            monthTotal = forecastPts.take(30).map { it.predicted }.moneySum()
        )
    }

    private fun aggregateByDay(invoices: List<ForecastInvoice>): List<DailySales> {
        val cal = Calendar.getInstance()
        return invoices
            .groupBy { inv ->
                cal.timeInMillis = inv.createdAt
                Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }
            .map { (ymd, invs) ->
                cal.set(ymd.first, ymd.second, ymd.third, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                DailySales(
                    dateMs    = cal.timeInMillis,
                    dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1,
                    amount    = invs.map { it.totalAmount }.moneySum()
                )
            }
            .sortedBy { it.dateMs }
    }

    private fun exponentialSmoothing(values: List<Double>): List<Double> {
        val result = mutableListOf(values.first())
        for (i in 1 until values.size) {
            result.add(alpha * values[i] + (1.0 - alpha) * result.last())
        }
        return result
    }

    private fun computeDayFactors(points: List<DailySales>): Map<Int, Double> {
        val overallAvg = points.map { it.amount }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
        return (0..6).associateWith { dow ->
            val dayVals = points.filter { it.dayOfWeek == dow }.map { it.amount }
            if (dayVals.isEmpty()) 1.0
            else {
                val dayAvg = dayVals.average()
                if (dayAvg.isNaN() || dayAvg == 0.0) 1.0
                else (dayAvg / overallAvg).coerceIn(0.1, 3.0)
            }
        }
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
}
