package com.verto.app.ui.screens.home

internal data class HomeScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)

internal fun sanitizeHomeScrollPosition(
    index: Int,
    offset: Int,
    itemCount: Int,
): HomeScrollPosition {
    if (itemCount <= 0) return HomeScrollPosition()
    return HomeScrollPosition(
        index = index.coerceIn(0, itemCount - 1),
        offset = offset.coerceAtLeast(0),
    )
}

internal fun resolveRestoredItemIndex(
    stableKeys: List<String>,
    restoredKey: String?,
    fallbackIndex: Int = 0,
): Int {
    if (stableKeys.isEmpty()) return 0
    val restoredIndex = restoredKey?.let(stableKeys::indexOf)?.takeIf { it >= 0 }
    return restoredIndex ?: fallbackIndex.coerceIn(0, stableKeys.lastIndex)
}

internal fun quickActionVisibleCount(
    availableWidthDp: Int,
    fontScale: Float,
): Int = when {
    fontScale >= 1.6f -> 2
    fontScale >= 1.3f -> 2
    availableWidthDp >= 420 -> 4
    availableWidthDp >= 280 -> 3
    else -> 2
}

internal fun adaptiveSearchGridColumnCount(
    availableWidthDp: Int,
    fontScale: Float,
): Int = when {
    availableWidthDp < 360 -> 2
    fontScale >= 1.3f -> 2
    else -> 3
}
