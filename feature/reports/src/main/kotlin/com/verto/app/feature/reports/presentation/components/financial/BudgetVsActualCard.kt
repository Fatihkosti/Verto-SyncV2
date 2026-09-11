package com.verto.app.feature.reports.presentation.components.financial

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import com.verto.app.feature.reports.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.bottomsheets.BudgetEditorSheet
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.utils.MoneyMath
import java.util.concurrent.TimeUnit

@Composable
fun BudgetVsActualCard(
    target: Double,
    actual: Double,
    progress: Float,
    budgets: List<BudgetItem> = emptyList(),
    onSaveBudget: (BudgetItem) -> Unit = {},
    onDeleteBudget: (BudgetItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetItem?>(null) }

    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_7e1714000003),
        icon = "🎯",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
        ) {
            // عرض كل الأهداف النشطة
            if (budgets.isEmpty()) {
                // الوضع الافتراضي: هدف مبيعات واحد من budgetTarget
                if (target > 0) {
                    BudgetProgressRow(
                        label    = BudgetType.SALES_TARGET.label,
                        actual   = actual,
                        target   = target,
                        progress = progress,
                        periodEnd = System.currentTimeMillis() + 86_400_000L * 15
                    )
                } else {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_b0e2d3843f5e),
                        color = TextMuted,
                        fontSize = ReportsTextScale.sp13
                    )
                }
            } else {
                budgets.forEach { budget ->
                    val actualForBudget = if (budget.budgetType == BudgetType.SALES_TARGET) actual else 0.0
                    val progressForBudget = if (budget.targetAmount > 0)
                        (actualForBudget / budget.targetAmount).toFloat().coerceIn(0f, 1f) else 0f

                    BudgetProgressRow(
                        label     = budget.budgetType.label,
                        actual    = actualForBudget,
                        target    = budget.targetAmount,
                        progress  = progressForBudget,
                        periodEnd = budget.periodEnd,
                        onEdit    = {
                            editingBudget = budget
                            showEditor = true
                        }
                    )
                }
            }

            // زرار إضافة هدف
            TextButton(
                onClick = {
                    editingBudget = null
                    showEditor = true
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_41cd1ecc4519), color = AccentPrimary, fontSize = ReportsTextScale.sp13)
            }
        }
    }

    if (showEditor) {
        BudgetEditorSheet(
            existingBudget = editingBudget,
            onSave         = onSaveBudget,
            onDismiss      = { showEditor = false }
        )
    }
}

@Composable
private fun BudgetProgressRow(
    label: String,
    actual: Double,
    target: Double,
    progress: Float,
    periodEnd: Long,
    onEdit: (() -> Unit)? = null
) {
    val remainingDays = ((periodEnd - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
    val remaining = (target - actual).coerceAtLeast(0.0)
    val dailyNeeded = if (remainingDays > 0) MoneyMath.divide(remaining, remainingDays.toDouble()) else 0.0
    val isAchieved = progress >= 1f

    Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextSecondary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fc9db15ab9d2, (progress * 100).toInt()),
                    color      = if (isAchieved) SuccessColor else AccentPrimary,
                    fontSize   = ReportsTextScale.sp20,
                    fontWeight = FontWeight.Bold
                )
                if (onEdit != null) {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(ReportsDimensions.dp4)) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_def78fe524d8), fontSize = ReportsTextScale.sp14)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_18773218c025, CurrencyFormatter.formatNoSymbol(actual), CurrencyFormatter.formatNoSymbol(target)),
                color = TextSecondary,
                fontSize = ReportsTextScale.sp13
            )
            if (remainingDays > 0) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_bde7530b76e5, remainingDays), color = TextMuted, fontSize = ReportsTextScale.sp12)
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(ReportsDimensions.dp8)
                .clip(RoundedCornerShape(ReportsDimensions.dp4)),
            color      = if (isAchieved) SuccessColor else AccentPrimary,
            trackColor = BgCardAlt
        )

        when {
            isAchieved -> Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_9816e2072bb7),
                color = SuccessColor, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.SemiBold
            )
            dailyNeeded > 0 -> Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a2a93b056454, CurrencyFormatter.formatNoSymbol(dailyNeeded)),
                color = TextMuted, fontSize = ReportsTextScale.sp12
            )
        }
    }
}
