package com.verto.app.ui.theme

import androidx.compose.ui.unit.dp

/** Shared component geometry. These values are not general spacing/foundation tokens. */
object VertoComponentSize {
    val buttonHeight = 52.dp
    val formFieldMinHeight = 52.dp
    val formStatusIcon = 20.dp
    val formContentMaxWidth = 520.dp
    val stepIndicatorDot = 8.dp
    val stepIndicatorActiveWidth = 24.dp
}

/** Settings-family component geometry retained centrally to avoid feature-local primitives. */
object VertoSettingsTokens {
    val cardRadius = 14.dp
    val sectionStartPadding = 4.dp
    val sectionTopPadding = 4.dp
    val sectionBottomPadding = 2.dp
    val dividerStartInset = 52.dp
    val rowVerticalPadding = 14.dp
    val rowIconGap = 14.dp
}

/** Geometry for compact shared legacy primitives while they are migrated domain-by-domain. */
object VertoSharedPrimitiveTokens {
    val debtProgressHeight = 6.dp
    val debtProgressRadius = 10.dp
    val statusDot = 8.dp
    val compactCardRadius = 14.dp
    val compactCardPadding = 14.dp
    val compactProgressHeight = 8.dp
    val infoChipHorizontalPadding = 10.dp
    val infoChipVerticalPadding = 5.dp
    val amountCurrencyBaselinePadding = 2.dp
}
