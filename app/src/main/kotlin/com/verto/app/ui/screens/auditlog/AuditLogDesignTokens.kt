package com.verto.app.ui.screens.auditlog

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Audit-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object AuditLogDimensions {
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp9 = 9.dp
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp24 = VertoSpacing.xl
    val dp36 = 36.dp
    val dp38 = 38.dp
    val dp48 = VertoSize.minTouchTarget
    val dp60 = 60.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object AuditLogTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
}
