package com.verto.app.ui.navigation.search

import com.verto.app.utils.SearchTextNormalizer
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import javax.inject.Inject

/** Search provider for app-shell screens and executable local actions. */
class AppCatalogHomeSearchProvider internal constructor(
    private val catalog: AppSearchCatalog,
) : HomeSearchProvider {
    @Inject constructor() : this(AppSearchCatalog.default)

    override val providerId: String = "app.navigation_actions"

    override suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        val keys = SearchTextNormalizer.query(query.text) ?: return emptyList()
        val normalizedQuery = keys.text
        if (normalizedQuery.isBlank()) return emptyList()

        val screens = catalog.screens.asSequence()
            .filter { context.allows(it.requiredPermission) }
            .mapNotNull { screen ->
                val score = matchScore(normalizedQuery, screen.name, screen.section.label, screen.keywords)
                score?.let {
                    ScoredResult(
                        score = it,
                        result = HomeSearchResult(
                            key = screen.destinationId,
                            title = screen.name,
                            subtitle = "شاشة • ${screen.section.label}",
                            kind = HomeSearchKind.SCREEN,
                            destination = HomeDestination(screen.destinationId),
                            requiredPermission = screen.requiredPermission,
                        ),
                    )
                }
            }

        val actions = catalog.actions.asSequence()
            .filter { context.allows(it.requiredPermission) }
            .mapNotNull { action ->
                val score = matchScore(normalizedQuery, action.name, action.section.label, action.keywords)
                score?.let {
                    ScoredResult(
                        score = it,
                        result = HomeSearchResult(
                            key = action.destinationId,
                            title = action.name,
                            subtitle = "إجراء • ${action.section.label}",
                            kind = HomeSearchKind.ACTION,
                            destination = HomeDestination(action.destinationId),
                            requiredPermission = action.requiredPermission,
                        ),
                    )
                }
            }

        return (screens + actions)
            .sortedWith(compareBy<ScoredResult> { it.score }.thenBy { it.result.title })
            .take(query.limit)
            .map(ScoredResult::result)
            .toList()
    }

    private fun matchScore(
        query: String,
        name: String,
        section: String,
        keywords: Set<String>,
    ): Int? {
        val values = sequenceOf(name, section) + keywords.asSequence()
        val normalized = values.map(SearchTextNormalizer::text).filter(String::isNotBlank).toList()
        val tokens = query.split(' ').filter(String::isNotBlank)
        if (tokens.any { token -> normalized.none { value -> token in value } }) return null

        val normalizedName = SearchTextNormalizer.text(name)
        return when {
            normalizedName == query -> 0
            normalizedName.startsWith(query) -> 1
            query in normalizedName -> 2
            normalized.any { it == query } -> 3
            normalized.any { it.startsWith(query) } -> 4
            else -> 5
        }
    }

    private data class ScoredResult(
        val score: Int,
        val result: HomeSearchResult,
    )
}
