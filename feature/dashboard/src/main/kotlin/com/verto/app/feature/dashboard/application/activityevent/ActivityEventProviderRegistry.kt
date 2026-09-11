package com.verto.app.feature.dashboard.application.activityevent

import com.verto.feature.dashboard.api.ActivityEventProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Stable registry supplied by Hilt multibindings. */
@Singleton
class ActivityEventProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards ActivityEventProvider>,
) {
    val all: List<ActivityEventProvider> = providers
        .onEach { provider ->
            require(provider.providerId.isNotBlank()) { "activity event providerId must not be blank" }
        }
        .sortedBy(ActivityEventProvider::providerId)
        .also { sorted ->
            val duplicateIds = sorted.groupingBy(ActivityEventProvider::providerId)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicateIds.isEmpty()) {
                "duplicate activity event provider ids: ${duplicateIds.sorted()}"
            }
        }
}
