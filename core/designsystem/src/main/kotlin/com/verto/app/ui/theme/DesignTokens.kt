package com.verto.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Foundation tokens shared by every Verto surface.
 *
 * Feature-specific geometry must not be added here. Shared component geometry belongs in
 * ComponentTokens.kt; feature geometry belongs to the owning feature.
 */
object VertoSpacing {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
    val massive = 64.dp
}

/** Common, feature-agnostic sizes only. */
object VertoSize {
    val minTouchTarget = 48.dp
    val iconSmall = 18.dp
    val iconMedium = 20.dp
    val iconLarge = 24.dp
    val iconHero = 32.dp
    val iconContainer = 40.dp
    val iconContainerLarge = 48.dp
    val fab = 56.dp
    val screenHorizontalPadding = 20.dp
    val contentMaxWidth = 560.dp
}

/** Insets owned by screen scaffolds; nested components consume the resulting content area. */
object VertoSafeArea {
    val horizontalPadding = VertoSize.screenHorizontalPadding
    val formMaxWidth = VertoSize.contentMaxWidth
}

object VertoRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 28.dp
}

object VertoElevation {
    val none = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val floating = 6.dp
}

object VertoStroke {
    val thin = 1.dp
    val progress = 2.dp
    val loading = 3.dp
}

object VertoAlpha {
    const val subtle = 0.08f
    const val soft = 0.12f
    const val faintBorder = 0.15f
    const val muted = 0.16f
    const val border = 0.24f
    const val statusBorder = 0.30f
    const val disabled = 0.38f
    const val activeBorder = 0.40f
    const val scrim = 0.48f
    const val secondaryContent = 0.60f
}

/** Motion roles; durations are intentionally centralized to prevent new raw timings. */
object VertoMotion {
    const val instant = 0
    const val fast = 120
    const val normal = 220
    const val slow = 360
    const val emphasized = 500
    const val attentionPulse = 800
    const val long = 1500
}
