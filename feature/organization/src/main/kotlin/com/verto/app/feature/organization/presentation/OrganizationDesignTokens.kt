package com.verto.app.feature.organization.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned compatibility geometry. Exact Core matches alias Foundation/component tokens. */
internal object OrganizationDimensions {
    val dp0 = VertoSpacing.none
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
    val dp17 = 17.dp
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp24 = VertoSpacing.xl
    val dp28 = 28.dp
    val dp32 = VertoSpacing.xxl
    val dp36 = 36.dp
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp52 = VertoComponentSize.buttonHeight
    val dp54 = 54.dp
    val dp72 = 72.dp
    val dp96 = 96.dp
}

/** Feature-owned compatibility type scale; exact Core type sizes alias VertoTypography. */
internal object OrganizationTextScale {
    val sp4 = 4.sp
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp17 = 17.sp
    val sp18 = VertoTypography.titleMedium.fontSize
    val sp20 = VertoTypography.titleLarge.fontSize
    val sp21 = 21.sp
    val sp28 = VertoTypography.headlineMedium.fontSize
}
