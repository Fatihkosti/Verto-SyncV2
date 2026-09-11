package com.verto.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantic geometry used by adaptive screens and screenshot tests. */
object VertoAdaptiveTokens {
    val compactMaxWidth = 599.dp
    val mediumMaxWidth = 839.dp
    val expandedMinWidth = 840.dp
    val safeContentPadding = VertoSafeArea.horizontalPadding

    /** Local available-width threshold for switching inline content to a stacked layout. */
    val inlineStackThreshold = 360.dp
    const val largeContentFontScale = 1.5f

    fun shouldStackInlineContent(availableWidth: Dp, fontScale: Float): Boolean =
        availableWidth < inlineStackThreshold || fontScale >= largeContentFontScale
}

/** Directional UI must opt into one of these explicit islands. */
enum class VertoTextDirectionIsland {
    Phone,
    Code,
    Identifier,
    NumericValue,
}
