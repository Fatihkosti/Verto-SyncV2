package com.verto.app.feature.reports.presentation.components.sales

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.analytics.ItemRealMargin
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*

@Composable
fun RealMarginCard(
    margins: List<ItemRealMargin>,
    modifier: Modifier = Modifier
) {
    if (margins.isEmpty()) return

    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d2184c02dfa7),
        icon = "📊",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)
        ) {
            // رأس الجدول
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_2a4bfa9d9d0e),    color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(2f))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_8c290852422c),   color = TextMuted, fontSize = ReportsTextScale.sp11)
                Spacer(Modifier.width(ReportsDimensions.dp20))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_b5ce96d4ea4d),   color = TextMuted, fontSize = ReportsTextScale.sp11)
            }

            margins.take(10).forEach { item ->
                MarginRow(item = item)
            }

            if (margins.any { !it.historicalCostComplete }) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a07db809b51a),
                    color = WarningColor,
                    fontSize = ReportsTextScale.sp11,
                    modifier = Modifier.padding(top = ReportsDimensions.dp4)
                )
            }
        }
    }
}

@Composable
private fun MarginRow(item: ItemRealMargin) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReportsDimensions.dp6))
            .background(BgCardAlt)
            .padding(horizontal = ReportsDimensions.dp12, vertical = ReportsDimensions.dp6),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.itemName,
            color    = TextPrimary,
            fontSize = ReportsTextScale.sp13,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f)
        )

        // هامش ظاهري
        MarginBadge(pct = item.realizedMarginPct)
        Spacer(Modifier.width(ReportsDimensions.dp8))
        // سهم الاتجاه
        Text(
            if (item.replacementMarginPct < item.realizedMarginPct) androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_80eff920af0d) else androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_21606782c65e),
            color = if (item.replacementMarginPct < item.realizedMarginPct) WarningColor else TextMuted,
            fontSize = ReportsTextScale.sp12
        )
        Spacer(Modifier.width(ReportsDimensions.dp8))
        // هامش حقيقي
        MarginBadge(pct = item.replacementMarginPct)
    }
}

@Composable
private fun MarginBadge(pct: Float) {
    val color = when {
        pct >= 30f -> SuccessColor
        pct >= 15f -> AccentPrimary
        pct >= 0f  -> WarningColor
        else       -> ErrorColor
    }
    Text(
        text = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fc9db15ab9d2, pct.toInt()),
        color = color,
        fontSize = ReportsTextScale.sp13,
        fontWeight = FontWeight.SemiBold
    )
}
