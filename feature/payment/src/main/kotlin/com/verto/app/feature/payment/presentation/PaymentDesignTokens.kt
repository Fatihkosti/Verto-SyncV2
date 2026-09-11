package com.verto.app.feature.payment.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned exact geometry retained during the v132 Design System migration. */
internal object PaymentDimensions {
    val dp0 = VertoSpacing.none
    val dp0_5 = 0.5.dp
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp4 = VertoSpacing.xxs
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp7 = 7.dp
    val dp8 = VertoSpacing.xs
    val dp9 = 9.dp
    val dp10 = 10.dp
    val dp11 = 11.dp
    val dp12 = VertoSpacing.sm
    val dp13 = 13.dp
    val dp14 = 14.dp
    val dp15 = 15.dp
    val dp16 = VertoSpacing.md
    val dp17 = 17.dp
    val dp18 = VertoSize.iconSmall
    val dp19 = 19.dp
    val dp20 = VertoSpacing.lg
    val dp21 = 21.dp
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp25 = 25.dp
    val dp28 = 28.dp
    val dp32 = VertoSpacing.xxl
    val dp36 = 36.dp
    val dp40 = VertoSpacing.xxxl
    val dp42 = 42.dp
    val dp44 = 44.dp
    val dp46 = 46.dp
    val dp48 = VertoSize.minTouchTarget
    val dp56 = VertoSize.fab
    val dp58 = 58.dp
    val dp212 = 212.dp
    val dp360 = 360.dp
}

/** Exact type-scale matches alias Core typography; legacy in-between sizes stay feature-owned. */
internal object PaymentTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp18 = 18.sp
    val sp20 = VertoTypography.titleLarge.fontSize
    val sp22 = 22.sp
    val sp23 = 23.sp
    val sp24 = VertoTypography.headlineSmall.fontSize
}
