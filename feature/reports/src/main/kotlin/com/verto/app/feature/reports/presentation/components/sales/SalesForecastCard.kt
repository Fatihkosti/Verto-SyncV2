package com.verto.app.feature.reports.presentation.components.sales

import com.verto.app.feature.reports.presentation.ReportChartDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.analytics.ForecastResult
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun SalesForecastCard(
    forecast: ForecastResult?,
    modifier: Modifier = Modifier
) {
    if (forecast == null || forecast.forecast.isEmpty()) return

    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_ab957490736a),
        icon = "📈",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportChartDimensions.dp16, vertical = ReportChartDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp12)
        ) {
            // بطاقتا الملخص
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp8)
            ) {
                ForecastSummaryCard(
                    label  = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a8615604b55c),
                    amount = forecast.weekTotal,
                    emoji  = "📅",
                    modifier = Modifier.weight(1f)
                )
                ForecastSummaryCard(
                    label  = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_01840db216d1),
                    amount = forecast.monthTotal,
                    emoji  = "🗓️",
                    modifier = Modifier.weight(1f)
                )
            }

            // الرسم البياني
            ForecastChart(forecast = forecast)

            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_417e93188975),
                color = TextMuted,
                fontSize = ReportsTextScale.sp11
            )
        }
    }
}

@Composable
private fun ForecastSummaryCard(
    label: String,
    amount: Double,
    emoji: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ReportChartDimensions.dp10))
            .background(BgCardAlt)
            .padding(ReportChartDimensions.dp12),
        verticalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp4)
    ) {
        Text(emoji, fontSize = ReportsTextScale.sp18)
        Text(
            CurrencyFormatter.formatNoSymbol(amount),
            color = AccentPrimary,
            fontSize = ReportsTextScale.sp16,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp11)
    }
}

@Composable
private fun ForecastChart(forecast: ForecastResult) {
    val historyColor   = AccentBlue
    val forecastColor  = AccentPrimary
    val bandColor      = AccentPrimary.copy(alpha = 0.12f)

    val historyAmounts = forecast.history.takeLast(14).map { it.amount }
    val forecastAmounts = forecast.forecast.take(14).map { it.predicted }
    val allValues = historyAmounts + forecastAmounts
    val maxVal = allValues.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReportChartDimensions.dp120)
    ) {
        val w = size.width
        val h = size.height
        val totalPoints = historyAmounts.size + forecastAmounts.size
        val stepX = if (totalPoints > 1) w / (totalPoints - 1).toFloat() else w

        fun xOf(i: Int) = i * stepX
        fun yOf(v: Double) = h - (v / maxVal * h).toFloat()

        // رسم نطاق الثقة للتوقع
        if (forecast.forecast.take(14).isNotEmpty()) {
            val bandPath = Path()
            val pts = forecast.forecast.take(14)
            val startX = historyAmounts.size * stepX
            bandPath.moveTo(startX, yOf(pts.first().upper))
            pts.forEachIndexed { i, pt ->
                bandPath.lineTo(startX + i * stepX, yOf(pt.upper))
            }
            pts.reversed().forEachIndexed { i, pt ->
                val idx = pts.size - 1 - i
                bandPath.lineTo(startX + idx * stepX, yOf(pt.lower))
            }
            bandPath.close()
            drawPath(bandPath, color = bandColor)
        }

        // رسم التاريخ
        drawPolyline(historyAmounts, stepX, h, maxVal, historyColor, strokeWidthDp = 2f)

        // رسم التوقع (منقط)
        val forecastStartX = historyAmounts.size * stepX
        drawPolyline(
            values = forecastAmounts,
            stepX = stepX,
            h = h,
            maxVal = maxVal,
            color = forecastColor,
            strokeWidthDp = 1.5f,
            offsetX = forecastStartX
        )

        // خط الفصل بين التاريخ والتوقع
        if (historyAmounts.isNotEmpty() && forecastAmounts.isNotEmpty()) {
            val sepX = historyAmounts.size * stepX
            drawLine(
                color = forecastColor.copy(alpha = 0.4f),
                start = Offset(sepX, 0f),
                end = Offset(sepX, h),
                strokeWidth = 1.5f
            )
        }
    }
}

private fun DrawScope.drawPolyline(
    values: List<Double>,
    stepX: Float,
    h: Float,
    maxVal: Double,
    color: Color,
    strokeWidthDp: Float,
    offsetX: Float = 0f
) {
    if (values.size < 2) return
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = offsetX + i * stepX
        val y = h - (v / maxVal * h).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidthDp * density))
}
