package com.verto.app.feature.management.presentation

import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

/**
 * Management-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object ManagementDimensions {
    val dp2 = VertoStroke.progress
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp24 = VertoSpacing.xl
    val dp22 = 22.dp
    val dp30 = 30.dp
    val dp320 = 320.dp
    val dp48 = VertoSize.minTouchTarget
}
