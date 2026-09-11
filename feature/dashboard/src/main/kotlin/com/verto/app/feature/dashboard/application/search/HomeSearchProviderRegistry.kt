package com.verto.app.feature.dashboard.application.search

import com.verto.feature.dashboard.api.HomeSearchProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable registry backed by Hilt multibindings.
 * Feature modules contribute providers without changing the search engine.
 */
@Singleton
class HomeSearchProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards HomeSearchProvider>,
) {
    val all: List<HomeSearchProvider> = providers
        .onEach { require(it.providerId.isNotBlank()) { "search providerId must not be blank" } }
        .sortedBy(HomeSearchProvider::providerId)
        .also { sorted ->
            val duplicateIds = sorted.groupingBy(HomeSearchProvider::providerId)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicateIds.isEmpty()) {
                "duplicate Home search providerId values: ${duplicateIds.sorted()}"
            }
        }
}
