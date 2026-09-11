package com.verto.app.ui.screens.expenses

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/** Feature-owned exact geometry retained during the v132 Design System migration. */
internal object ExpensesDimensions {
    val dp1 = VertoStroke.thin
    val dp4 = VertoSpacing.xxs
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp18 = VertoSize.iconSmall
    val dp20 = VertoSpacing.lg
    val dp40 = VertoSpacing.xxxl
}

/** Exact type-scale matches alias Core typography; legacy in-between sizes stay feature-owned. */
internal object ExpensesTextScale {
    val sp10 = 10.sp
    val sp11 = VertoTypography.labelSmall.fontSize
    val sp13 = 13.sp
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp32 = VertoTypography.headlineLarge.fontSize
}
