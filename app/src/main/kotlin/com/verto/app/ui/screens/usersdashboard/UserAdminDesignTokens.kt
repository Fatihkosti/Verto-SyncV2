package com.verto.app.ui.screens.usersdashboard

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned compatibility geometry. Exact Core matches alias Foundation/component tokens. */
internal object UserAdminDimensions {
    val dp2 = VertoStroke.progress
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp28 = 28.dp
    val dp90 = 90.dp
}

/** Feature-owned compatibility type scale; exact Core type sizes alias VertoTypography. */
internal object UserAdminTextScale {
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp18 = VertoTypography.titleMedium.fontSize
}
