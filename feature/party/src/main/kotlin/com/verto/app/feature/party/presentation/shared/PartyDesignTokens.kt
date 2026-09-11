package com.verto.app.feature.party.presentation.shared

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Party-owned exact geometry used to preserve the current client/supplier/competitor UI during v133.
 * Shared foundation values alias Core tokens; specialized legacy geometry remains Party-owned rather
 * than being promoted into the global foundation solely to remove literals.
 */
internal object PartyDimensions {
    val dp0 = VertoSpacing.none
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp9 = 9.dp
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp13 = 13.dp
    val dp14 = 14.dp
    val dp15 = 15.dp
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp26 = 26.dp
    val dp28 = 28.dp
    val dp32 = VertoSpacing.xxl
    val dp36 = 36.dp
    val dp40 = VertoSpacing.xxxl
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp52 = VertoComponentSize.buttonHeight
    val dp60 = 60.dp
    val dp70 = 70.dp
    val dp80 = 80.dp
}

/**
 * Party text-size compatibility scale. Exact matches consume VertoTypography; legacy in-between
 * sizes stay Party-owned so migration does not silently redesign or reflow existing screens.
 */
internal object PartyTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp18 = VertoTypography.titleMedium.fontSize
    val sp20 = VertoTypography.titleLarge.fontSize
    val sp22 = 22.sp
    val sp40 = VertoTypography.displayMedium.fontSize
}
