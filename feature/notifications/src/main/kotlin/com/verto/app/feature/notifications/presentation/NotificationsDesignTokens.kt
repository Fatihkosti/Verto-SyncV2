package com.verto.app.feature.notifications.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Notifications-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object NotificationsDimensions {
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp8 = VertoSpacing.xs
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object NotificationsTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp15 = 15.sp
}
