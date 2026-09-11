package com.verto.app.ui.screens.home.search

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchResult

/** Collision-safe identity across independent providers and entity kinds. */
internal fun HomeSearchResult.stableSearchKey(): String = "${providerId}:${kind}:${key}"

/**
 * Resolves a click only from freshly re-queried canonical results.
 * UI-carried destinations/actions are intentionally ignored.
 */
internal fun resolveCanonicalSearchDestination(
    canonicalResults: List<HomeSearchResult>,
    resultKey: String,
    actionId: String,
    context: HomePermissionContext,
    isDestinationValid: (HomeDestination) -> Boolean,
): HomeDestination? {
    val canonical = canonicalResults.firstOrNull { result ->
        result.stableSearchKey() == resultKey
    } ?: return null

    val destination = when (actionId) {
        HOME_SEARCH_OPEN_RESULT_ACTION_ID -> {
            if (!context.allows(canonical.requiredPermission)) return null
            canonical.destination
        }

        else -> {
            val canonicalAction = canonical.actions.firstOrNull { action -> action.id == actionId }
                ?: return null
            if (!context.allows(canonicalAction.requiredPermission)) return null
            canonicalAction.destination
        }
    }
    return destination.takeIf(isDestinationValid)
}

internal const val HOME_SEARCH_OPEN_RESULT_ACTION_ID: String = "open_result"
