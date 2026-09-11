package com.verto.app.ui.screens.commission

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Commission-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object CommissionDimensions {
    val dp0 = VertoSpacing.none
    val dp0_5 = 0.5.dp
    val dp1_5 = 1.5.dp
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp7 = 7.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp24 = VertoSpacing.xl
    val dp32 = VertoSpacing.xxl
    val dp120 = 120.dp
    val dp320 = 320.dp
    val dp360 = 360.dp
    val dp480 = 480.dp
    val dp520 = 520.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object CommissionTextScale {
    val sp8 = 8.sp
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
    val sp36 = VertoTypography.displaySmall.fontSize
}
