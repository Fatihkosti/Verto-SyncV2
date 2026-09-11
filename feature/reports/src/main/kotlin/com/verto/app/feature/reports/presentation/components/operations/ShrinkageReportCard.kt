package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.ShrinkageData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun ShrinkageReportCard(
    data: ShrinkageData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d2469281d468), icon = "📉", initiallyExpanded = false, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10)
        ) {
            if (data.adjustmentCount == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp12),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f00581141ea9), color = SuccessColor, fontSize = ReportsTextScale.sp13)
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ReportsDimensions.dp10))
                        .background(ErrorContainer)
                        .padding(ReportsDimensions.dp14),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f61a2da6307a), fontSize = ReportsTextScale.sp22)
                    Text(
                        CurrencyFormatter.formatNoSymbol(data.totalLoss),
                        color = ErrorColor,
                        fontSize = ReportsTextScale.sp16,
                        fontWeight = FontWeight.Bold
                    )
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_dd4ecb9ae650), color = TextMuted, fontSize = ReportsTextScale.sp11)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ReportsDimensions.dp10))
                        .background(BgCardAlt)
                        .padding(ReportsDimensions.dp14),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_edc47290ed60), fontSize = ReportsTextScale.sp22)
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_c79f71243f41, data.adjustmentCount),
                        color = TextPrimary,
                        fontSize = ReportsTextScale.sp16,
                        fontWeight = FontWeight.Bold
                    )
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_90c621821f6d), color = TextMuted, fontSize = ReportsTextScale.sp11)
                }
            }

            if (data.totalLoss > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ReportsDimensions.dp8))
                        .background(ErrorContainer)
                        .padding(ReportsDimensions.dp10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fa3fbbae1e98),
                        color = ErrorColor,
                        fontSize = ReportsTextScale.sp12
                    )
                }
            }
        }
    }
}
