package com.verto.app.ui.screens.home

internal enum class HomeFabPrimaryAction {
    SALES,
    PURCHASES,
}

internal data class HomeFabPolicy(
    val primaryAction: HomeFabPrimaryAction?,
    val supportsInternationalPurchase: Boolean,
) {
    val isVisible: Boolean get() = primaryAction != null

    val contentDescription: String
        get() = when (primaryAction) {
            HomeFabPrimaryAction.SALES -> "إنشاء فاتورة بيع"
            HomeFabPrimaryAction.PURCHASES -> "إنشاء فاتورة شراء"
            null -> ""
        }
}

internal fun resolveHomeFabPolicy(
    canCreateSales: Boolean,
    canCreatePurchases: Boolean,
): HomeFabPolicy = when {
    canCreateSales -> HomeFabPolicy(
        primaryAction = HomeFabPrimaryAction.SALES,
        supportsInternationalPurchase = canCreatePurchases,
    )
    canCreatePurchases -> HomeFabPolicy(
        primaryAction = HomeFabPrimaryAction.PURCHASES,
        supportsInternationalPurchase = true,
    )
    else -> HomeFabPolicy(
        primaryAction = null,
        supportsInternationalPurchase = false,
    )
}
