package com.verto.app.feature.dashboard.application.search

import com.verto.app.utils.SearchTextNormalizer
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.supervisorScope

/**
 * Aggregates independent local-first Home search providers.
 * Provider failures are isolated while coroutine cancellation is always propagated.
 */
@Singleton
class UnifiedHomeSearchUseCase @Inject constructor(
    private val registry: HomeSearchProviderRegistry,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observe(
        queries: Flow<HomeSearchQuery>,
        context: HomePermissionContext,
    ): Flow<List<HomeSearchResult>> = queries
        .distinctUntilChanged()
        .debounce(DEBOUNCE_MILLIS)
        .mapLatest { query -> invoke(query, context) }
        .distinctUntilChanged()

    suspend operator fun invoke(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        val queryKeys = SearchTextNormalizer.query(query.text) ?: return emptyList()
        val providerQuery = query.copy(limit = minOf(query.limit, MAX_RESULTS_PER_PROVIDER))

        val aggregated = supervisorScope {
            registry.all.map { provider ->
                async { provider.searchSafely(providerQuery, context) }
            }.awaitAll().flatten()
        }

        val ranked = aggregated
            .asSequence()
            .filter { result -> context.allows(result.requiredPermission) }
            .map { result ->
                val allowedActions = result.actions.filter { action ->
                    context.allows(action.requiredPermission)
                }
                RankedResult(
                    result = if (allowedActions.size == result.actions.size) {
                        result
                    } else {
                        result.copy(actions = allowedActions)
                    },
                    match = matchQuality(queryKeys, result),
                )
            }
            .sortedWith(RANKING)
            .distinctBy { rankedResult ->
                rankedResult.result.kind to rankedResult.result.key
            }
            .toList()

        val perKindCounts = mutableMapOf<HomeSearchKind, Int>()
        val totalLimit = minOf(query.limit, MAX_TOTAL_RESULTS)
        return buildList(totalLimit) {
            for (rankedResult in ranked) {
                if (size >= totalLimit) break
                val kind = rankedResult.result.kind
                val count = perKindCounts[kind] ?: 0
                if (count >= MAX_RESULTS_PER_KIND) continue
                add(rankedResult.result)
                perKindCounts[kind] = count + 1
            }
        }
    }

    private suspend fun HomeSearchProvider.searchSafely(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        val startedAt = System.nanoTime()
        return try {
            val results = search(query, context)
                .take(MAX_RESULTS_PER_PROVIDER)
                .map { result -> result.copy(providerId = providerId) }
            HomeSearchDiagnostics.recordSuccess(
                providerId = providerId,
                queryLength = query.text.length,
                resultCount = results.size,
                elapsedNanos = System.nanoTime() - startedAt,
            )
            results
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            HomeSearchDiagnostics.recordFailure(
                providerId = providerId,
                queryLength = query.text.length,
                error = error,
                elapsedNanos = System.nanoTime() - startedAt,
            )
            emptyList()
        }
    }

    private fun matchQuality(
        query: SearchTextNormalizer.QueryKeys,
        result: HomeSearchResult,
    ): MatchQuality {
        val queryVariants = sequenceOf(query.text, query.identifier, query.phone)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        val valueVariants = sequenceOf(result.title, result.subtitle.orEmpty())
            .flatMap { value ->
                sequenceOf(
                    SearchTextNormalizer.text(value),
                    SearchTextNormalizer.identifier(value),
                    SearchTextNormalizer.phone(value),
                )
            }
            .filter(String::isNotBlank)
            .distinct()
            .toList()

        return when {
            valueVariants.any { value -> queryVariants.any(value::equals) } -> MatchQuality.EXACT
            valueVariants.any { value -> queryVariants.any(value::startsWith) } -> MatchQuality.PREFIX
            valueVariants.any { value -> queryVariants.any(value::contains) } -> MatchQuality.PARTIAL
            else -> MatchQuality.PROVIDER_MATCH
        }
    }

    private data class RankedResult(
        val result: HomeSearchResult,
        val match: MatchQuality,
    )

    private enum class MatchQuality {
        EXACT,
        PREFIX,
        PARTIAL,
        PROVIDER_MATCH,
    }

    companion object {
        const val DEBOUNCE_MILLIS: Long = 180L
        const val MAX_RESULTS_PER_KIND: Int = 6
        const val MAX_RESULTS_PER_PROVIDER: Int = 24
        const val MAX_TOTAL_RESULTS: Int = 24

        private val RANKING = compareBy<RankedResult> { ranked -> ranked.match.ordinal }
            .thenByDescending { ranked -> ranked.result.updatedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy { ranked -> ranked.result.kind.ordinal }
            .thenBy { ranked -> ranked.result.title }
            .thenBy { ranked -> ranked.result.key }
            .thenBy { ranked -> ranked.result.providerId }
    }
}

internal data class HomeSearchDiagnosticSnapshot(
    val providerId: String,
    val successCount: Long,
    val failureCount: Long,
    val lastResultCount: Int,
    val lastQueryLength: Int,
    val lastDurationBucket: String,
    val lastFailureType: String?,
)

/** PII-free in-process diagnostics; raw query text and entity data are never retained. */
internal object HomeSearchDiagnostics {
    private data class MutableCounters(
        val successCount: AtomicLong = AtomicLong(),
        val failureCount: AtomicLong = AtomicLong(),
        @Volatile var lastResultCount: Int = 0,
        @Volatile var lastQueryLength: Int = 0,
        @Volatile var lastDurationBucket: String = "unknown",
        @Volatile var lastFailureType: String? = null,
    )

    private val counters = ConcurrentHashMap<String, MutableCounters>()

    fun recordSuccess(providerId: String, queryLength: Int, resultCount: Int, elapsedNanos: Long) {
        val entry = counters.computeIfAbsent(providerId) { MutableCounters() }
        entry.successCount.incrementAndGet()
        entry.lastResultCount = resultCount
        entry.lastQueryLength = queryLength
        entry.lastDurationBucket = durationBucket(elapsedNanos)
        entry.lastFailureType = null
    }

    fun recordFailure(providerId: String, queryLength: Int, error: Exception, elapsedNanos: Long) {
        val entry = counters.computeIfAbsent(providerId) { MutableCounters() }
        entry.failureCount.incrementAndGet()
        entry.lastResultCount = 0
        entry.lastQueryLength = queryLength
        entry.lastDurationBucket = durationBucket(elapsedNanos)
        entry.lastFailureType = error.javaClass.simpleName.ifBlank { "Exception" }
    }

    fun snapshot(providerId: String): HomeSearchDiagnosticSnapshot? = counters[providerId]?.let { entry ->
        HomeSearchDiagnosticSnapshot(
            providerId = providerId,
            successCount = entry.successCount.get(),
            failureCount = entry.failureCount.get(),
            lastResultCount = entry.lastResultCount,
            lastQueryLength = entry.lastQueryLength,
            lastDurationBucket = entry.lastDurationBucket,
            lastFailureType = entry.lastFailureType,
        )
    }

    internal fun resetForTests() = counters.clear()

    private fun durationBucket(elapsedNanos: Long): String = when (elapsedNanos / 1_000_000L) {
        in 0..9 -> "lt_10ms"
        in 10..49 -> "10_49ms"
        in 50..199 -> "50_199ms"
        else -> "gte_200ms"
    }
}
