package com.verto.app.ui.components

import com.verto.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.ReportPeriod
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsFilterBar(
    selectedPeriod: ReportPeriod,
    activeFrom: Long,
    activeTo: Long,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRange: (Long, Long) -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val displayFrom = if (activeFrom > 0L) activeFrom else System.currentTimeMillis()
    val displayTo   = if (activeTo   > 0L) activeTo   else System.currentTimeMillis()

    // ── الصف الأفقي القابل للتمرير ─────────────────────────────────
    // الترتيب: منتقي التاريخ أوّلاً (على اليمين في RTL) ثم الأزرار السريعة
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .padding(vertical = AppChromeDimensions.dp10)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(AppChromeDimensions.dp8))

            // ── منتقي التاريخ المخصص (أوّل الصف دائماً) ──────────
            DateRangeChip(
                from    = displayFrom,
                to      = displayTo,
                isActive = selectedPeriod == ReportPeriod.CUSTOM,
                onFromClick = { showFromPicker = true },
                onToClick   = { showToPicker   = true }
            )

            // ── فاصل ──────────────────────────────────────────────
            Box(
                Modifier
                    .width(AppChromeDimensions.dp1)
                    .height(AppChromeDimensions.dp28)
                    .background(BorderColor)
            )

            // ── الأزرار السريعة (كل الفترات ما عدا CUSTOM) ────────
            ReportPeriod.entries
                .filter { it != ReportPeriod.CUSTOM }
                .forEach { period ->
                    PeriodChip(
                        label    = period.label,
                        selected = selectedPeriod == period,
                        onClick  = { onPeriodSelected(period) }
                    )
                }

            Spacer(Modifier.width(AppChromeDimensions.dp8))
        }
    }

    HorizontalDivider(color = BorderColor)

    // ── Date Pickers ────────────────────────────────────────────────
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = displayFrom)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onCustomRange(c.timeInMillis, activeTo.takeIf { it > 0 } ?: c.timeInMillis)
                        showFromPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = displayTo)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                        }
                        onCustomRange(activeFrom.takeIf { it > 0 } ?: displayFrom, c.timeInMillis)
                        showToPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// منتقي النطاق المخصص (من - إلى) في أوّل الصف
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun DateRangeChip(
    from: Long,
    to: Long,
    isActive: Boolean,
    onFromClick: () -> Unit,
    onToClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppChromeDimensions.dp10))
            .background(if (isActive) AccentPrimary.copy(alpha = 0.12f) else BgCard)
            .border(
                AppChromeDimensions.dp1,
                if (isActive) AccentPrimary else BorderColor,
                RoundedCornerShape(AppChromeDimensions.dp10)
            )
            .padding(horizontal = AppChromeDimensions.dp2, vertical = AppChromeDimensions.dp2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp2)
    ) {
        // زر "من"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppChromeDimensions.dp8))
                .clickable { onFromClick() }
                .padding(horizontal = AppChromeDimensions.dp8, vertical = AppChromeDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp4)
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                tint = if (isActive) AccentPrimary else TextMuted,
                modifier = Modifier.size(AppChromeDimensions.dp13)
            )
            Column {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_aa7099e27834), color = TextMuted, fontSize = AppChromeTextScale.sp9)
                Text(
                    DateUtils.formatDate(from),
                    color = if (isActive) AccentPrimary else TextPrimary,
                    fontSize = AppChromeTextScale.sp11,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // فاصل داخلي
        Box(
            Modifier
                .width(AppChromeDimensions.dp1)
                .height(AppChromeDimensions.dp20)
                .background(BorderColor)
        )

        // زر "إلى"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppChromeDimensions.dp8))
                .clickable { onToClick() }
                .padding(horizontal = AppChromeDimensions.dp8, vertical = AppChromeDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp4)
        ) {
            Column {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_8ab80326e0b9), color = TextMuted, fontSize = AppChromeTextScale.sp9)
                Text(
                    DateUtils.formatDate(to),
                    color = if (isActive) AccentPrimary else TextPrimary,
                    fontSize = AppChromeTextScale.sp11,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// زر فترة سريعة
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppChromeDimensions.dp10))
            .background(if (selected) AccentPrimary else BgCard)
            .border(
                AppChromeDimensions.dp1,
                if (selected) AccentPrimary else BorderColor,
                RoundedCornerShape(AppChromeDimensions.dp10)
            )
            .clickable { onClick() }
            .padding(horizontal = AppChromeDimensions.dp14, vertical = AppChromeDimensions.dp8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = if (selected) BgDeep else TextSecondary,
            fontSize   = AppChromeTextScale.sp12,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}