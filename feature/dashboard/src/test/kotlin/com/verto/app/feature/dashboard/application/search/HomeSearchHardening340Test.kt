package com.verto.app.feature.dashboard.application.search

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchHardening340Test {
    private val context = HomePermissionContext("org-a", "user-a", setOf("allowed"))

    @Test
    fun `aggregator remains bounded with 1000 rows per provider and stamps provider identity`() = runBlocking {
        val providers = listOf(
            fakeProvider("party", HomeSearchKind.PARTY, 1_000),
            fakeProvider("invoice", HomeSearchKind.INVOICE, 1_000),
            fakeProvider("inventory", HomeSearchKind.INVENTORY_ITEM, 1_000),
            fakeProvider("payment", HomeSearchKind.PAYMENT, 1_000),
        )
        val result = UnifiedHomeSearchUseCase(HomeSearchProviderRegistry(providers.toSet()))(
            HomeSearchQuery("ab", 24),
            context,
        )
        assertEquals(24, result.size)
        assertTrue(result.all { it.providerId.isNotBlank() })
        assertTrue(result.groupingBy { it.kind }.eachCount().values.all { it <= 6 })
    }

    @Test
    fun `provider failure is isolated and diagnosed without losing healthy results`() = runBlocking {
        HomeSearchDiagnostics.resetForTests()
        val healthy = fakeProvider("healthy", HomeSearchKind.PARTY, 3)
        val failing = object : HomeSearchProvider {
            override val providerId = "failing"
            override suspend fun search(query: HomeSearchQuery, context: HomePermissionContext): List<HomeSearchResult> {
                error("boom")
            }
        }
        val result = UnifiedHomeSearchUseCase(HomeSearchProviderRegistry(setOf(healthy, failing)))(
            HomeSearchQuery("ab", 24),
            context,
        )
        assertEquals(3, result.size)
        val snapshot = HomeSearchDiagnostics.snapshot("failing")
        assertNotNull(snapshot)
        assertEquals(1L, snapshot!!.failureCount)
        assertEquals(2, snapshot.lastQueryLength)
        assertEquals("IllegalStateException", snapshot.lastFailureType)
    }

    @Test(expected = CancellationException::class)
    fun `provider cancellation propagates`() = runBlocking {
        val cancelling = object : HomeSearchProvider {
            override val providerId = "cancel"
            override suspend fun search(query: HomeSearchQuery, context: HomePermissionContext): List<HomeSearchResult> {
                throw CancellationException("cancelled")
            }
        }
        UnifiedHomeSearchUseCase(HomeSearchProviderRegistry(setOf(cancelling)))(
            HomeSearchQuery("ab", 24),
            context,
        )
        Unit
    }

    @Test
    fun `permission defense at merge removes unauthorized result`() = runBlocking {
        val blocked = object : HomeSearchProvider {
            override val providerId = "blocked"
            override suspend fun search(query: HomeSearchQuery, context: HomePermissionContext) = listOf(
                result("blocked-1", HomeSearchKind.PARTY, requiredPermission = "missing"),
            )
        }
        val result = UnifiedHomeSearchUseCase(HomeSearchProviderRegistry(setOf(blocked)))(
            HomeSearchQuery("ab", 24),
            context,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `registry rejects duplicate provider ids`() {
        val first = fakeProvider("duplicate", HomeSearchKind.PARTY, 1)
        val second = fakeProvider("duplicate", HomeSearchKind.INVOICE, 1)
        try {
            HomeSearchProviderRegistry(setOf(first, second))
            throw AssertionError("duplicate provider ids must fail")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun fakeProvider(id: String, kind: HomeSearchKind, count: Int) = object : HomeSearchProvider {
        override val providerId: String = id
        override suspend fun search(query: HomeSearchQuery, context: HomePermissionContext): List<HomeSearchResult> =
            (0 until count).map { index -> result("$id-$index", kind) }
    }

    private fun result(
        key: String,
        kind: HomeSearchKind,
        requiredPermission: String? = null,
    ) = HomeSearchResult(
        key = key,
        title = "ab $key",
        kind = kind,
        destination = HomeDestination("screen"),
        requiredPermission = requiredPermission,
        updatedAtEpochMillis = key.hashCode().toLong(),
    )
}
