package com.verto.app.feature.reports.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentLight
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.GoldLight
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography
import com.verto.app.ui.theme.WarningColor

/** Report-owned compatibility geometry. Shared values alias the central Foundation. */
internal object ReportsDimensions {
    val dp0 = VertoSpacing.none
    val dp0_5 = 0.5.dp
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp7 = 7.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp30 = 30.dp
    val dp36 = 36.dp
    val dp40 = VertoSpacing.xxxl
    val dp42 = 42.dp
    val dp48 = VertoSize.minTouchTarget
    val dp50 = 50.dp
    val dp96 = 96.dp
    val dp120 = 120.dp
    val dp240 = 240.dp
}

/** Report-owned compatibility type scale; exact central type sizes reuse VertoTypography. */
internal object ReportsTextScale {
    val sp8 = 8.sp
    val sp9 = 9.sp
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp17 = 17.sp
    val sp18 = VertoTypography.titleMedium.fontSize
    val sp20 = VertoTypography.titleLarge.fontSize
    val sp22 = 22.sp
    val sp32 = VertoTypography.headlineLarge.fontSize
}

/**
 * Chart-component geometry is deliberately Report-owned. It must not be promoted to Foundation:
 * these values describe chart cells, legends, plots and chart component layout, not app spacing.
 */
internal object ReportChartDimensions {
    val dp1 = ReportsDimensions.dp1
    val dp2 = ReportsDimensions.dp2
    val dp4 = ReportsDimensions.dp4
    val dp8 = ReportsDimensions.dp8
    val dp10 = ReportsDimensions.dp10
    val dp12 = ReportsDimensions.dp12
    val dp16 = ReportsDimensions.dp16
    val dp30 = ReportsDimensions.dp30
    val dp120 = ReportsDimensions.dp120
}

/** Report-owned categorical chart series; semantic states still come from the central color API. */
@Composable
internal fun reportCategorySeriesColors(): List<Color> = listOf(
    AccentPrimary,
    AccentBlue,
    SuccessColor,
    GoldPrimary,
    WarningColor,
    ErrorColor,
    AccentLight,
    GoldLight,
)
