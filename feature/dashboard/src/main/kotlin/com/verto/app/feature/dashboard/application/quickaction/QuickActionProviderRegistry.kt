package com.verto.app.feature.dashboard.application.quickaction

import com.verto.feature.dashboard.api.QuickActionProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickActionProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards QuickActionProvider>,
) {
    val all: List<QuickActionProvider> = providers
        .sortedBy(QuickActionProvider::providerId)
        .also { sorted ->
            require(sorted.none { provider -> provider.providerId.isBlank() }) {
                "quick action provider ids must not be blank"
            }
            val duplicateProviderIds = sorted.groupingBy(QuickActionProvider::providerId)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicateProviderIds.isEmpty()) {
                "duplicate quick action provider ids: ${duplicateProviderIds.sorted()}"
            }

            val declaredIds = sorted.flatMap { provider -> provider.actionIds }
            require(declaredIds.none(String::isBlank)) {
                "quick action provider declarations must not contain blank ids"
            }
            val duplicateActionIds = declaredIds.groupingBy { it }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicateActionIds.isEmpty()) {
                "duplicate declared quick action ids: ${duplicateActionIds.sorted()}"
            }
        }
}
