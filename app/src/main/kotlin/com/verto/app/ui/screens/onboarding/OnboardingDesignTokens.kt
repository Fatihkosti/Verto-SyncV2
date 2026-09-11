package com.verto.app.ui.screens.onboarding

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Onboarding-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */
internal object OnboardingDimensions {
    val dp3 = VertoStroke.loading
    val dp4 = VertoSpacing.xxs
    val dp8 = VertoSpacing.xs
    val dp10 = 10.dp
    val dp12 = VertoSpacing.sm
    val dp16 = VertoSpacing.md
    val dp20 = VertoSpacing.lg
    val dp24 = VertoSpacing.xl
    val dp32 = VertoSpacing.xxl
    val dp40 = VertoSpacing.xxxl
    val dp56 = VertoSize.fab
    val dp60 = 60.dp
    val dp120 = 120.dp
}

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object OnboardingTextScale {
    val sp12 = VertoTypography.bodySmall.fontSize
    val sp14 = VertoTypography.bodyMedium.fontSize
    val sp15 = 15.sp
    val sp16 = VertoTypography.bodyLarge.fontSize
    val sp24 = VertoTypography.headlineSmall.fontSize
    val sp28 = VertoTypography.headlineMedium.fontSize
    val sp64 = 64.sp
}
