package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*

@Composable
fun AuditLogTodayCard(
    todayCount: Int,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_114c47b3bf91),
        icon = "📝",
        initiallyExpanded = false,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
        ) {
            if (todayCount == 0) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_60711b529299), color = SuccessColor, fontSize = ReportsTextScale.sp13)
            } else {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_171c0d88b69c, todayCount),
                    color = if (todayCount > 10) ErrorColor else WarningColor,
                    fontSize = ReportsTextScale.sp14,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
