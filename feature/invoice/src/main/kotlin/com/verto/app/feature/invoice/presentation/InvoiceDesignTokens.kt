package com.verto.app.feature.invoice.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned exact geometry retained during the v132 Design System migration. */
internal object InvoiceDimensions {
    val dp0 = VertoSpacing.none
    val dp1 = VertoStroke.thin
    val dp2 = VertoStroke.progress
    val dp4 = VertoSpacing.xxs
    val dp5 = 5.dp
    val dp6 = 6.dp
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp14 = 14.dp
    val dp15 = 15.dp
    val dp16 = VertoSpacing.md
    val dp20 = VertoSpacing.lg
    val dp22 = 22.dp
    val dp24 = VertoSpacing.xl
    val dp30 = 30.dp
    val dp32 = VertoSpacing.xxl
    val dp36 = 36.dp
    val dp42 = 42.dp
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp54 = 54.dp
    val dp56 = VertoSize.fab
    val dp60 = 60.dp
    val dp112 = 112.dp
    val dp120 = 120.dp
}

/** Exact type-scale matches alias Core typography; legacy in-between sizes stay feature-owned. */
internal object InvoiceTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp17 = 17.sp
    val sp18 = 18.sp
    val sp22 = 22.sp
    val sp30 = 30.sp
}
