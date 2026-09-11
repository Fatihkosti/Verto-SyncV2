package com.verto.app.feature.inventory.presentation.inventory

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoTypography

/**
 * Inventory-owned exact geometry used to preserve the current Inventory UI during v131 migration.
 * Shared foundation values alias Core tokens when the existing value is an exact match; the remaining
 * values are feature-specific geometry and must not be promoted to Core merely to remove literals.
 */
internal object InventoryDimensions {
    val dp0 = VertoSpacing.none
    val dp0_6 = 0.6.dp
    val dp1 = VertoStroke.thin
    val dp2 = 2.dp
    val dp3 = 3.dp
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
    val dp13_5 = 13.5.dp
    val dp14 = 14.dp
    val dp15 = 15.dp
    val dp16 = VertoSpacing.md
    val dp16_5 = 16.5.dp
    val dp17 = 17.dp
    val dp18 = VertoSize.iconSmall
    val dp19 = 19.dp
    val dp20 = VertoSpacing.lg
    val dp21 = 21.dp
    val dp22 = 22.dp
    val dp25 = 25.dp
    val dp26 = 26.dp
    val dp28 = 28.dp
    val dp30 = 30.dp
    val dp32 = VertoSpacing.xxl
    val dp34 = 34.dp
    val dp36 = 36.dp
    val dp38 = 38.dp
    val dp40 = VertoSpacing.xxxl
    val dp43 = 43.dp
    val dp44 = 44.dp
    val dp48 = VertoSize.minTouchTarget
    val dp50 = 50.dp
    val dp52 = VertoComponentSize.buttonHeight
    val dp60 = 60.dp
    val dp63 = 63.dp
    val dp72 = 72.dp
    val dp74 = 74.dp
    val dp77 = 77.dp
    val dp78 = 78.dp
    val dp86 = 86.dp
    val dp90 = 90.dp
    val dp100 = 100.dp
    val dp108 = 108.dp
    val dp116 = 116.dp
    val dp120 = 120.dp
    val dp180 = 180.dp
    val dp232 = 232.dp
    val dp420 = 420.dp
    val dpNegative3 = -dp3
    val dpNegative7 = -dp7
}

/**
 * Inventory text-size compatibility scale. Exact matches consume the central VertoTypography scale;
 * legacy in-between sizes remain feature-owned so v131 does not redesign or reflow the screens.
 */
internal object InventoryTextScale {
    val sp8 = 8.sp
    val sp8_5 = 8.5.sp
    val sp9 = 9.sp
    val sp9_5 = 9.5.sp
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
    val sp22 = 22.sp
    val sp26 = 26.sp
    val sp40 = VertoTypography.displayMedium.fontSize
}
