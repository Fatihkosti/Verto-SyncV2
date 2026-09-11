package com.verto.app.feature.profile.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Profile-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object ProfileDimensions {
    val dp2 = VertoStroke.progress
    val dp4 = VertoSpacing.xxs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object ProfileTextScale {
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp15 = 15.sp
}
