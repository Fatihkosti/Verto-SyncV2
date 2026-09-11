package com.verto.app.feature.integration.optimal.presentation

import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

/**
 * Optimal-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object OptimalDimensions {
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp9 = 9.dp
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp28 = 28.dp
    val dp30 = 30.dp
    val dp32 = VertoSpacing.xxl
    val dp46 = 46.dp
    val dp48 = VertoSize.minTouchTarget
    val dp150 = 150.dp
    val dp300 = 300.dp
}
