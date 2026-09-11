package com.verto.app.ui.screens.home

import androidx.compose.ui.unit.dp

/**
 * Geometry owned by the Home work-center. It is intentionally outside :core:designsystem because
 * these values describe one feature rather than the shared visual foundation.
 */
internal object HomeDesignTokens {
    val screenHorizontalPadding = 16.dp
    val quickActionMinHeight = 84.dp
    val quickActionMinWidth = 84.dp
    val pendingCardHeight = 196.dp
    val pendingActionCardHeight = 188.dp
    val pendingActionButtonHeight = 48.dp
    val pendingActionButtonWidth = 144.dp
    val pendingIllustrationWidth = 84.dp
    val pendingInlineAction = 48.dp
    val notificationBadge = 16.dp
    val notificationDot = 8.dp
    val headerAction = 36.dp
    val headerIcon = 24.dp
    val pagerIndicator = 7.dp
    val pagerIndicatorActive = 9.dp
    val activityRowMinHeight = 48.dp
    val activityEmptyStateHeight = 148.dp
    const val activityMaxVisibleRows = 5
    val activityIcon = 20.dp
    val dialogMaxWidth = 560.dp
    val dialogScrollableMinHeight = 120.dp
    val activityBottomPadding = 100.dp
    const val dialogMaxHeightFraction = 0.88f
}
