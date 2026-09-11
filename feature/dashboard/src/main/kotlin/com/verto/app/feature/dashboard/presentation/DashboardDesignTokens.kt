package com.verto.app.feature.dashboard.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Dashboard-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object DashboardDimensions {
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp24 = VertoSpacing.xl
    val dp32 = VertoSpacing.xxl
    val dp36 = 36.dp
    val dp72 = 72.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object DashboardTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
}
