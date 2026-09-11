package com.verto.app.ui.screens.home.search

import com.verto.app.core.error.UserErrorPresentation
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchResult

internal enum class HomeSearchFilter(
    val label: String,
    val kinds: Set<HomeSearchKind>,
) {
    ALL("الكل", HomeSearchKind.entries.toSet()),
    PARTIES("العملاء", setOf(HomeSearchKind.PARTY)),
    INVOICES("الفواتير", setOf(HomeSearchKind.INVOICE)),
    INVENTORY("المخزون", setOf(HomeSearchKind.INVENTORY_ITEM)),
    PAYMENTS("المدفوعات", setOf(HomeSearchKind.PAYMENT)),
    NAVIGATION("الشاشات", setOf(HomeSearchKind.SCREEN, HomeSearchKind.ACTION)),
}

internal data class HomeSearchUiState(
    val query: String = "",
    val selectedFilter: HomeSearchFilter = HomeSearchFilter.ALL,
    val results: List<HomeSearchResult> = emptyList(),
    val expandedResultKey: String? = null,
    val isLoading: Boolean = false,
    val error: UserErrorPresentation? = null,
) {
    val normalizedQuery: String = query.trim()
    val needsMoreCharacters: Boolean = normalizedQuery.length == 1
    val isWaitingForQuery: Boolean = normalizedQuery.isEmpty()
    val showEmptyState: Boolean =
        normalizedQuery.length >= MIN_QUERY_LENGTH && !isLoading && error == null && results.isEmpty()

    fun isExpanded(result: HomeSearchResult): Boolean =
        expandedResultKey == result.stableSearchKey()

    companion object {
        const val MIN_QUERY_LENGTH: Int = 2
    }
}
