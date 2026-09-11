package com.verto.app.feature.reports.presentation.components.core

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.components.DateRangePickerDialog
import com.verto.app.ui.theme.*
import com.verto.app.utils.ReportPeriod

@Composable
fun PeriodSelectorPill(
    selected: ReportPeriod,
    activeFrom: Long,
    activeTo: Long,
    onSelect: (ReportPeriod) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomRange by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
        horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
    ) {
        ReportPeriod.entries.forEach { period ->
            PeriodChip(
                label  = period.label,
                active = selected == period,
                onClick = {
                    if (period == ReportPeriod.CUSTOM) showCustomRange = true
                    else onSelect(period)
                }
            )
        }
    }

    if (showCustomRange) {
        DateRangePickerDialog(
            initialFrom = activeFrom.takeIf { it > 0L },
            initialTo = activeTo.takeIf { it > 0L },
            onConfirm = { from, to ->
                onCustomRange(from, to)
                showCustomRange = false
            },
            onDismiss = { showCustomRange = false },
        )
    }
}

@Composable
private fun PeriodChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bgColor    = if (active) AccentPrimary else BgCard
    val textColor  = if (active) TextOnAccent else TextSecondary
    val borderMod  = if (active) Modifier else Modifier.border(ReportsDimensions.dp1, BorderColor, RoundedCornerShape(ReportsDimensions.dp20))

    Box(
        modifier = borderMod
            .clip(RoundedCornerShape(ReportsDimensions.dp20))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = ReportsDimensions.dp14, vertical = ReportsDimensions.dp7),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = textColor,
            fontSize   = ReportsTextScale.sp13,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
