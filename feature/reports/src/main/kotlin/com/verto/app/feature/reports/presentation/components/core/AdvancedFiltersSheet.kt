package com.verto.app.feature.reports.presentation.components.core

import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoBottomSheet

// ─────────────────────────────────────────────────────
// Bottom Sheet للفلاتر المتقدمة
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFiltersSheet(
    filters: ReportsFilters,
    availableCategories: List<String>,
    availableCashiers: List<String>,
    onFiltersChange: (ReportsFilters) -> Unit,
    onDismiss: () -> Unit
) {
    VertoBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = ReportsDimensions.dp20, topEnd = ReportsDimensions.dp20)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = ReportsDimensions.dp20)
                .padding(bottom = ReportsDimensions.dp40)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp20)
        ) {
            // ── عنوان الـ Sheet ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v365_1_sales_filters_title), color = TextPrimary, fontSize = ReportsTextScale.sp18, fontWeight = FontWeight.Bold)
                if (filters.isActive) {
                    TextButton(onClick = { onFiltersChange(ReportsFilters()) }) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_04e7c7e15802, filters.activeCount), color = ErrorColor, fontSize = ReportsTextScale.sp13)
                    }
                }
            }

            // ── طريقة الدفع ─────────────────────────────
            FilterSection(
                title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a0de7bd18afc),
                options = listOf("نقدي", "آجل"),
                selected = filters.paymentMethod,
                onSelect = { option ->
                    onFiltersChange(filters.copy(paymentMethod = if (option == filters.paymentMethod) null else option))
                }
            )

            // ── التصنيف ─────────────────────────────────
            if (availableCategories.isNotEmpty()) {
                FilterSection(
                    title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_e90c6f6b4ca5),
                    options = availableCategories.take(12),
                    selected = filters.category,
                    onSelect = { option ->
                        onFiltersChange(filters.copy(category = if (option == filters.category) null else option))
                    }
                )
            }

            // ── الكاشير / الموظف ─────────────────────────
            if (availableCashiers.isNotEmpty()) {
                FilterSection(
                    title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_c18a63a0e24d),
                    options = availableCashiers.take(8),
                    selected = filters.cashierName,
                    onSelect = { option ->
                        onFiltersChange(filters.copy(cashierName = if (option == filters.cashierName) null else option))
                    }
                )
            }

            Spacer(Modifier.height(ReportsDimensions.dp4))

            VertoButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(ReportsDimensions.dp50),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(ReportsDimensions.dp14)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_6aa6a8999aa7), color = TextOnAccent, fontSize = ReportsTextScale.sp15, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10)) {
        Text(title, color = TextSecondary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)) {
            items(options) { option ->
                val active = selected == option
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ReportsDimensions.dp20))
                        .background(if (active) AccentPrimary else BgCardAlt)
                        .border(ReportsDimensions.dp1, if (active) AccentPrimary else BorderColor, RoundedCornerShape(ReportsDimensions.dp20))
                        .clickable { onSelect(option) }
                        .padding(horizontal = ReportsDimensions.dp14, vertical = ReportsDimensions.dp7),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (active) TextOnAccent else TextSecondary,
                        fontSize = ReportsTextScale.sp13,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// شريط الفلاتر النشطة
// ─────────────────────────────────────────────────────

@Composable
fun ActiveFilterChipsRow(
    filters: ReportsFilters,
    onFiltersChange: (ReportsFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!filters.isActive) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ReportsDimensions.dp16),
        horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
    ) {
        filters.paymentMethod?.let { method ->
            item {
                ActiveChip(label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_cf4c7e7f8e0f, method)) {
                    onFiltersChange(filters.copy(paymentMethod = null))
                }
            }
        }
        filters.category?.let { cat ->
            item {
                ActiveChip(label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_75229fff33f1, cat)) {
                    onFiltersChange(filters.copy(category = null))
                }
            }
        }
        filters.cashierName?.let { cashier ->
            item {
                ActiveChip(label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_81bed2295a10, cashier)) {
                    onFiltersChange(filters.copy(cashierName = null))
                }
            }
        }
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp20))
            .background(AccentDim)
            .padding(start = ReportsDimensions.dp10, end = ReportsDimensions.dp6, top = ReportsDimensions.dp6, bottom = ReportsDimensions.dp6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
    ) {
        Text(text = label, color = AccentLight, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_remove),
            tint = AccentLight,
            modifier = Modifier.size(ReportsDimensions.dp16).clickable(onClick = onRemove)
        )
    }
}
