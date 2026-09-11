package com.verto.app.ui.screens.messages

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Messages-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object MessagesDimensions {
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp20 = VertoSpacing.lg
    val dp24 = VertoSpacing.xl
    val dp36 = 36.dp
    val dp40 = VertoSpacing.xxxl
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp56 = VertoSize.fab
    val dp100 = 100.dp
    val dp220 = 220.dp
    val dp280 = 280.dp
    val dp360 = 360.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object MessagesTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp18 = VertoTypography.titleMedium.fontSize
}
