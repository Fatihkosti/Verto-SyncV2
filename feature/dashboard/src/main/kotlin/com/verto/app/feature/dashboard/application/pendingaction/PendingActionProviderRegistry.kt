package com.verto.app.feature.dashboard.application.pendingaction

import com.verto.feature.dashboard.api.PendingActionProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Stable registry supplied by Hilt multibindings. */
@Singleton
class PendingActionProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards PendingActionProvider>,
) {
    val all: List<PendingActionProvider> = providers
        .onEach { provider ->
            require(provider.providerId.isNotBlank()) { "pending action providerId must not be blank" }
        }
        .sortedBy(PendingActionProvider::providerId)
        .also { sorted ->
            val duplicateIds = sorted.groupingBy(PendingActionProvider::providerId)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicateIds.isEmpty()) {
                "duplicate pending action provider ids: ${duplicateIds.sorted()}"
            }
        }
}
