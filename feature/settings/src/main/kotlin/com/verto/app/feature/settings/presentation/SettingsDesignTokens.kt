package com.verto.app.feature.settings.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned compatibility geometry. Exact Core matches alias Foundation/component tokens. */
object SettingsDimensions {
    val dp0_5 = 0.5.dp
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp9 = 9.dp
    val dp10 = 10.dp
    val dp11 = 11.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp15 = 15.dp
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp26 = 26.dp
    val dp28 = 28.dp
    val dp30 = 30.dp
    val dp40 = VertoSpacing.xxxl
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp64 = VertoSpacing.massive
}

/** Feature-owned compatibility type scale; exact Core type sizes alias VertoTypography. */
object SettingsTextScale {
    val sp8 = 8.sp
    val sp9 = 9.sp
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp20 = VertoTypography.titleLarge.fontSize
}
