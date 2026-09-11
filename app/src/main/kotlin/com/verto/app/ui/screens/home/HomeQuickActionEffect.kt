package com.verto.app.ui.screens.home

import com.verto.feature.dashboard.api.HomeDestination

sealed interface HomeQuickActionEffect {
    data class Navigate(val destination: HomeDestination) : HomeQuickActionEffect
    data object ShowExpenseDialog : HomeQuickActionEffect
    data object ShowInternationalPurchaseDialog : HomeQuickActionEffect
    data object ExpenseSaved : HomeQuickActionEffect
}
