package com.verto.app.feature.reports.presentation.bottomsheets

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import com.verto.app.feature.reports.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import java.util.Calendar
import com.verto.app.ui.components.VertoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorSheet(
    existingBudget: BudgetItem? = null,
    onSave: (BudgetItem) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetType  by remember { mutableStateOf(existingBudget?.budgetType  ?: BudgetType.SALES_TARGET) }
    var periodType  by remember { mutableStateOf(existingBudget?.periodType  ?: BudgetPeriodType.MONTHLY) }
    var targetText  by remember { mutableStateOf(existingBudget?.targetAmount?.toString() ?: "") }
    var noteText    by remember { mutableStateOf(existingBudget?.note        ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    VertoBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = BgCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReportsDimensions.dp24, vertical = ReportsDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp16)
        ) {
            Text(
                if (existingBudget == null) androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_b08ac30bacd0_2) else androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_b08ac30bacd0),
                color      = TextPrimary,
                fontSize   = ReportsTextScale.sp18,
                fontWeight = FontWeight.Bold
            )

            // نوع الهدف
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_36231274535b), color = TextSecondary, fontSize = ReportsTextScale.sp13)
            Row(horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)) {
                BudgetType.entries.forEach { type ->
                    FilterChip(
                        selected = budgetType == type,
                        onClick  = { budgetType = type },
                        label    = { Text(type.label, fontSize = ReportsTextScale.sp12) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                            selectedLabelColor     = AccentPrimary
                        )
                    )
                }
            }

            // الفترة الزمنية
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_42cd7d14646e), color = TextSecondary, fontSize = ReportsTextScale.sp13)
            Row(horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)) {
                listOf(BudgetPeriodType.MONTHLY, BudgetPeriodType.WEEKLY, BudgetPeriodType.QUARTERLY).forEach { pt ->
                    FilterChip(
                        selected = periodType == pt,
                        onClick  = { periodType = pt },
                        label    = { Text(pt.label, fontSize = ReportsTextScale.sp12) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                            selectedLabelColor     = AccentPrimary
                        )
                    )
                }
            }

            // المبلغ المستهدف
            VertoOutlinedTextField(
                value  = targetText,
                onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' } },
                label  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_720c1915a124)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentPrimary,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor    = AccentPrimary,
                    cursorColor          = AccentPrimary,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary
                )
            )

            // ملاحظة
            VertoOutlinedTextField(
                value  = noteText,
                onValueChange = { noteText = it },
                label  = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note_optional)) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentPrimary,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor    = AccentPrimary,
                    cursorColor          = AccentPrimary,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary
                )
            )

            // أزرار الحفظ والإلغاء
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
            ) {
                VertoOutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }

                VertoButton(
                    onClick  = {
                        targetText.toDoubleOrNull()?.let { target ->
                            val (start, end) = periodRange(periodType)
                            val entity = existingBudget?.copy(
                                budgetType   = budgetType,
                                periodType   = periodType,
                                periodStart  = start,
                                periodEnd    = end,
                                targetAmount = target,
                                note         = noteText,
                                updatedAt    = System.currentTimeMillis()
                            ) ?: BudgetItem(
                                budgetType   = budgetType,
                                periodType   = periodType,
                                periodStart  = start,
                                periodEnd    = end,
                                targetAmount = target,
                                note         = noteText
                            )
                            onSave(entity)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save), color = TextOnAccent)
                }
            }

            Spacer(Modifier.height(ReportsDimensions.dp16))
        }
    }
}

private fun periodRange(type: BudgetPeriodType): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    return when (type) {
        BudgetPeriodType.DAILY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1); cal.add(Calendar.MILLISECOND, -1)
            start to cal.timeInMillis
        }
        BudgetPeriodType.WEEKLY -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 7); cal.add(Calendar.MILLISECOND, -1)
            start to cal.timeInMillis
        }
        BudgetPeriodType.MONTHLY -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            start to cal.timeInMillis
        }
        BudgetPeriodType.QUARTERLY -> {
            val month = cal.get(Calendar.MONTH)
            val quarterStart = (month / 3) * 3
            cal.set(Calendar.MONTH, quarterStart)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.MONTH, quarterStart + 2)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            start to cal.timeInMillis
        }
        BudgetPeriodType.YEARLY -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            start to cal.timeInMillis
        }
    }
}
