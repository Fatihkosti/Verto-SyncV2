package com.verto.app.ui.screens.home.search

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Search-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object HomeSearchDimensions {
    val dp1 = VertoStroke.thin
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp24 = VertoSpacing.xl
    val dp44 = 44.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object HomeSearchTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp17 = 17.sp
    val sp20 = VertoTypography.titleLarge.fontSize
    val sp21 = 21.sp
}
