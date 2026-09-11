package com.verto.app.feature.reports.presentation.components.financial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.reports.presentation.FinancialDiagnosticsData
import com.verto.app.feature.reports.presentation.InternationalSupplierStatementRow
import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.money.Money
import com.verto.app.ui.theme.BgCardAlt
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor

@Composable
fun FinancialIntegrityCard(data: FinancialDiagnosticsData, modifier: Modifier = Modifier) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f6518a88d43d), icon = "✓", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8),
        ) {
            val currency = data.functionalCurrencyCode.ifBlank { "—" }
            Text(
                text = if (data.isHealthy) androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_9fb5c59ce495_2) else androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_9fb5c59ce495),
                color = if (data.isHealthy) SuccessColor else WarningColor,
                fontSize = ReportsTextScale.sp13,
                fontWeight = FontWeight.SemiBold,
            )
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_5e9dcf2e62a8, currency), color = TextMuted, fontSize = ReportsTextScale.sp11)

            data.checks.forEach { check ->
                val hasIssue = check.differenceMinor != 0L || check.affectedCount > 0
                IntegrityRow(
                    label = check.label,
                    value = if (!hasIssue) "0" else buildString {
                        if (check.differenceMinor != 0L) {
                            append(Money.ofMinor(check.differenceMinor, data.functionalCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY }).toPlainString())
                            if (data.functionalCurrencyCode.isNotBlank()) append(" ${data.functionalCurrencyCode}")
                        }
                        if (check.affectedCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${check.affectedCount} سجل")
                        }
                    },
                    healthy = !hasIssue,
                )
            }

            IntegrityRow(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_54272a6c18fa),
                value = "${Money.ofMinor(data.inventoryOperationalValueMinor, data.functionalCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY }).toPlainString()} $currency",
                healthy = true,
            )

            data.dataQualityIssues.filter { it.count > 0 }.forEach { issue ->
                IntegrityRow(issue.label, "${issue.count} سجل", healthy = false)
            }
            if (data.outboxRequiresReviewCount > 0) {
                IntegrityRow("أحداث مزامنة تحتاج مراجعة", "${data.outboxRequiresReviewCount}", healthy = false)
            }
        }
    }
}

@Composable
fun InternationalSupplierStatementCard(
    rows: List<InternationalSupplierStatementRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_12a9718c2702), icon = "FX", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8),
        ) {
            rows.take(10).forEach { row ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ReportsDimensions.dp8))
                        .background(BgCardAlt).padding(ReportsDimensions.dp10),
                    verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.supplierName, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.SemiBold)
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_adebc50ade66, row.invoiceNumber), color = TextMuted, fontSize = ReportsTextScale.sp11)
                    }
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_7ce401fb5e3e, money(row.originalAmountMinor, row.transactionCurrencyCode), money(row.paidTransactionAmountMinor, row.transactionCurrencyCode), money(row.remainingTransactionAmountMinor, row.transactionCurrencyCode)),
                        color = TextSecondary, fontSize = ReportsTextScale.sp11,
                    )
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_1edd54148815, money(row.functionalCashPaidMinor, row.functionalCurrencyCode), money(row.realizedFxDifferenceMinor, row.functionalCurrencyCode)),
                        color = if (row.realizedFxDifferenceMinor == 0L) TextMuted else WarningColor,
                        fontSize = ReportsTextScale.sp11,
                    )
                    if (row.invoiceExchangeRateSnapshot.isNotBlank()) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_4a08db34c9ec, row.invoiceExchangeRateSnapshot), color = TextMuted, fontSize = ReportsTextScale.sp11)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrityRow(label: String, value: String, healthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ReportsDimensions.dp8))
            .background(BgCardAlt).padding(horizontal = ReportsDimensions.dp10, vertical = ReportsDimensions.dp8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = ReportsTextScale.sp12, modifier = Modifier.weight(1f))
        Text(value, color = if (healthy) SuccessColor else ErrorColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)
    }
}

private fun money(minor: Long, currency: String): String =
    "${Money.ofMinor(minor, currency.ifBlank { Money.TRANSACTION_CURRENCY }).toPlainString()} ${currency.ifBlank { "—" }}"
