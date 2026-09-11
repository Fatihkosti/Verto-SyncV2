package com.verto.app.ui.screens.home.search

import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSearchClickPolicy340Test {
    private val viewPermission = "sales_view"
    private val editPermission = "sales_edit"
    private val canonicalDestination = HomeDestination("invoice_details", mapOf("invoiceId" to "inv-1"))
    private val editDestination = HomeDestination("invoice_edit", mapOf("invoiceId" to "inv-1"))
    private val result = HomeSearchResult(
        key = "inv-1",
        providerId = "invoice.business",
        title = "INV-1",
        kind = HomeSearchKind.INVOICE,
        destination = canonicalDestination,
        actions = listOf(HomeAction("edit", "تعديل", editDestination, editPermission)),
        requiredPermission = viewPermission,
    )

    @Test
    fun `canonical current destination is accepted`() {
        val destination = resolveCanonicalSearchDestination(
            canonicalResults = listOf(result),
            resultKey = result.stableSearchKey(),
            actionId = HOME_SEARCH_OPEN_RESULT_ACTION_ID,
            context = context(setOf(viewPermission)),
            isDestinationValid = { true },
        )
        assertEquals(canonicalDestination, destination)
    }

    @Test
    fun `stale or unknown result is rejected`() {
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = emptyList(),
                resultKey = result.stableSearchKey(),
                actionId = HOME_SEARCH_OPEN_RESULT_ACTION_ID,
                context = context(setOf(viewPermission)),
                isDestinationValid = { true },
            ),
        )
    }

    @Test
    fun `revoked result permission is rejected`() {
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = listOf(result),
                resultKey = result.stableSearchKey(),
                actionId = HOME_SEARCH_OPEN_RESULT_ACTION_ID,
                context = context(emptySet()),
                isDestinationValid = { true },
            ),
        )
    }

    @Test
    fun `forged action id is rejected`() {
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = listOf(result),
                resultKey = result.stableSearchKey(),
                actionId = "forged",
                context = context(setOf(viewPermission, editPermission)),
                isDestinationValid = { true },
            ),
        )
    }

    @Test
    fun `revoked action permission is rejected`() {
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = listOf(result),
                resultKey = result.stableSearchKey(),
                actionId = "edit",
                context = context(setOf(viewPermission)),
                isDestinationValid = { true },
            ),
        )
    }

    @Test
    fun `malformed destination fails closed`() {
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = listOf(result),
                resultKey = result.stableSearchKey(),
                actionId = HOME_SEARCH_OPEN_RESULT_ACTION_ID,
                context = context(setOf(viewPermission)),
                isDestinationValid = { false },
            ),
        )
    }

    @Test
    fun `provider identity prevents cross-provider key collision`() {
        val other = result.copy(providerId = "other.provider")
        assertNull(
            resolveCanonicalSearchDestination(
                canonicalResults = listOf(other),
                resultKey = result.stableSearchKey(),
                actionId = HOME_SEARCH_OPEN_RESULT_ACTION_ID,
                context = context(setOf(viewPermission)),
                isDestinationValid = { true },
            ),
        )
    }

    private fun context(permissions: Set<String>) = HomePermissionContext(
        organizationId = "org-a",
        userId = "user-a",
        grantedPermissions = permissions,
    )
}
