package com.verto.app.feature.reports.presentation.components.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.reports.presentation.LandedCostVarianceData
import com.verto.app.feature.reports.presentation.OperationalAnalyticsAlert
import com.verto.app.feature.reports.presentation.PurchasePriceVarianceData
import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale
import com.verto.app.feature.reports.presentation.SupplierFxVarianceData
import com.verto.app.feature.reports.presentation.SupplierPaymentTimingData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.money.Money
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.feature.reports.R
import kotlin.math.abs

@Composable
fun InvoiceAnalyticsF255Card(
    ppv: PurchasePriceVarianceData,
    landed: LandedCostVarianceData,
    fx: SupplierFxVarianceData,
    paymentTiming: SupplierPaymentTimingData,
    alerts: List<OperationalAnalyticsAlert>,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = stringResource(R.string.reports_invoice_analytics_title), icon = "📊", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12),
        ) {
            PpvSection(ppv)
            LandedSection(landed)
            FxSection(fx)
            PaymentTimingSection(paymentTiming)
            AlertsSection(alerts)
        }
    }
}

@Composable
private fun PpvSection(data: PurchasePriceVarianceData) {
    SectionTitle(stringResource(R.string.reports_ppv_title))
    if (data.groups.isEmpty()) {
        EmptyMetric(stringResource(R.string.reports_ppv_empty))
    } else data.groups.forEach { group ->
        MetricRow(group.currencyCode, stringResource(R.string.reports_ppv_net, money(group.netVarianceMinor, group.currencyCode)), group.netVarianceMinor > 0L)
    }
}

@Composable
private fun LandedSection(data: LandedCostVarianceData) {
    SectionTitle(stringResource(R.string.reports_landed_variance_title))
    data.shipments.take(5).forEach { shipment ->
        MetricRow(shipment.shipmentNumber, money(shipment.varianceMinor, shipment.currencyCode), shipment.varianceMinor > 0L)
    }
    if (data.shipments.isEmpty()) EmptyMetric(stringResource(R.string.reports_landed_variance_empty))
}

@Composable
private fun FxSection(data: SupplierFxVarianceData) {
    SectionTitle(stringResource(R.string.reports_fx_variance_title))
    data.groups.forEach { group ->
        MetricRow(group.currencyCode, money(group.totalRealizedGainLossMinor, group.currencyCode), group.totalRealizedGainLossMinor < 0L)
    }
    if (data.groups.isEmpty()) EmptyMetric(stringResource(R.string.reports_fx_variance_empty))
}

@Composable
private fun PaymentTimingSection(data: SupplierPaymentTimingData) {
    Text(stringResource(R.string.reports_supplier_payment_average, data.portfolioAverageDays.toInt()), color = TextSecondary, fontSize = ReportsTextScale.sp13)
    data.suppliers.take(5).forEach { row ->
        MetricRow(
            row.supplierName,
            stringResource(R.string.reports_supplier_payment_row, row.averageDaysToPay.toInt(), signedDays(row.varianceFromPortfolioDays)),
            row.varianceFromPortfolioDays > 0f,
        )
    }
}

@Composable
private fun AlertsSection(alerts: List<OperationalAnalyticsAlert>) {
    SectionTitle(stringResource(R.string.reports_operational_alerts_title, alerts.size))
    alerts.take(8).forEach { alert ->
        val value = alert.amountMinor?.let { minor -> money(minor, alert.currencyCode.orEmpty()) }
        Text(
            listOfNotNull(alert.title, alert.message, value).joinToString(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_23659c52a66a)),
            color = when (alert.severity.name) {
                "CRITICAL" -> ErrorColor
                "WARNING" -> WarningColor
                else -> TextSecondary
            },
            fontSize = ReportsTextScale.sp12,
        )
    }
    if (alerts.isEmpty()) Text(stringResource(R.string.reports_operational_alerts_empty), color = SuccessColor, fontSize = ReportsTextScale.sp12)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyMetric(text: String) {
    Text(text, color = TextMuted, fontSize = ReportsTextScale.sp12)
}

@Composable
private fun MetricRow(label: String, value: String, warning: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = ReportsTextScale.sp12, modifier = Modifier.weight(1f))
        Text(value, color = if (warning) WarningColor else TextPrimary, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)
    }
}

private fun money(minor: Long, currencyCode: String): String {
    val code = currencyCode.ifBlank { Money.TRANSACTION_CURRENCY }
    return "${Money.ofMinor(minor, code).toPlainString()} ${currencyCode.ifBlank { "—" }}"
}

private fun signedDays(value: Float): String = when {
    value > 0f -> "+${abs(value).toInt()}"
    value < 0f -> "-${abs(value).toInt()}"
    else -> "0"
}
