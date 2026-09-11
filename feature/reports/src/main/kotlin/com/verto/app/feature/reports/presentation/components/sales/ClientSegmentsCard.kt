package com.verto.app.feature.reports.presentation.components.sales

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import com.verto.app.feature.reports.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun ClientSegmentsCard(
    segments: List<RfmSegmentSummary>,
    topClvClients: List<ClientClvData>,
    onSegmentClick: (ReportRfmSegment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_b7157f8f7065),
        icon = "👥",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
        ) {
            // شبكة الشرائح
            segments.sortedByDescending { segmentPriority(it.segment) }.forEach { seg ->
                SegmentRow(seg = seg, onClick = { onSegmentClick(seg.segment) })
            }

            // أعلى عملاء بـ CLV
            if (topClvClients.isNotEmpty()) {
                Spacer(Modifier.height(ReportsDimensions.dp8))
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_6b4b45560f86),
                    color = TextSecondary,
                    fontSize = ReportsTextScale.sp13,
                    fontWeight = FontWeight.SemiBold
                )
                topClvClients.take(5).forEachIndexed { _, client ->
                    ClvClientRow(client)
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(seg: RfmSegmentSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReportsDimensions.dp8))
            .background(BgCardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = ReportsDimensions.dp12, vertical = ReportsDimensions.dp8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)) {
            Text(seg.segment.emoji, fontSize = ReportsTextScale.sp16)
            Text(seg.segment.label, color = TextPrimary, fontSize = ReportsTextScale.sp14, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp16)) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_8290561bcac3, seg.count),
                color = AccentPrimary,
                fontSize = ReportsTextScale.sp13,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                CurrencyFormatter.formatNoSymbol(seg.totalSpent),
                color = TextMuted,
                fontSize = ReportsTextScale.sp12
            )
        }
    }
}

@Composable
private fun ClvClientRow(client: ClientClvData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ReportsDimensions.dp4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            client.clientName.ifBlank { client.rfm.clientId.take(12) },
            color = TextSecondary,
            fontSize = ReportsTextScale.sp13,
            modifier = Modifier.weight(1f)
        )
        Text(
            CurrencyFormatter.formatNoSymbol(client.projectedClv),
            color = SuccessColor,
            fontSize = ReportsTextScale.sp13,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun segmentPriority(seg: ReportRfmSegment): Int = when (seg) {
    ReportRfmSegment.CHAMPIONS         -> 10
    ReportRfmSegment.LOYAL             -> 9
    ReportRfmSegment.POTENTIAL_LOYALIST -> 8
    ReportRfmSegment.NEW_CUSTOMERS     -> 7
    ReportRfmSegment.PROMISING         -> 6
    ReportRfmSegment.NEEDS_ATTENTION   -> 5
    ReportRfmSegment.AT_RISK           -> 4
    ReportRfmSegment.CANT_LOSE         -> 3
    ReportRfmSegment.HIBERNATING       -> 2
    ReportRfmSegment.LOST              -> 1
}
