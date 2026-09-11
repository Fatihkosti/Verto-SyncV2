package com.verto.app.feature.reports.presentation.components.financial

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
import com.verto.app.feature.reports.presentation.AgedPayablesData
import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.money.Money
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.feature.reports.R

@Composable
fun AgedPayablesCard(data: AgedPayablesData, modifier: Modifier = Modifier) {
    CollapsibleSection(title = stringResource(R.string.reports_ap_aging_title), icon = "📤", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10),
        ) {
            Text(
                stringResource(R.string.reports_ap_aging_total, formatMinor(data.grandTotalMinor, data.currencyCode), data.dpoDays.toInt()),
                color = if (data.grandTotalMinor > 0L) WarningColor else SuccessColor,
                fontSize = ReportsTextScale.sp14,
                fontWeight = FontWeight.Bold,
            )
            val buckets = listOf(
                "0–30" to data.total0_30Minor,
                "31–60" to data.total31_60Minor,
                "61–90" to data.total61_90Minor,
                "+90" to data.totalOver90Minor,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                buckets.forEach { (label, amount) ->
                    Column {
                        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp10)
                        Text(formatMinor(amount, data.currencyCode), color = if (amount > 0L) ErrorColor else TextMuted, fontSize = ReportsTextScale.sp11)
                    }
                }
            }
            data.suppliers.take(5).forEach { supplier ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(supplier.supplierName, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.reports_ap_oldest_due, supplier.oldestDueDays), color = TextSecondary, fontSize = ReportsTextScale.sp11)
                    }
                    Text(formatMinor(supplier.totalMinor, supplier.currencyCode), color = ErrorColor, fontSize = ReportsTextScale.sp12)
                }
            }
        }
    }
}

private fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val currency = currencyCode.ifBlank { Money.TRANSACTION_CURRENCY }
    return "${Money.ofMinor(amountMinor, currency).toPlainString()} ${currencyCode.ifBlank { "—" }}"
}
