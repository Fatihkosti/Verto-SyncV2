package com.verto.app.feature.dashboard.application.quickaction

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeStorageScope
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionDestinationIds
import com.verto.feature.dashboard.api.QuickActionIds
import com.verto.feature.dashboard.api.QuickActionOrderStore
import com.verto.feature.dashboard.api.QuickActionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionHardening338Test {
    private val context = HomePermissionContext("org-a", "user-a", setOf("allow"))

    @Test fun `registry rejects blank provider id and duplicate declarations`() {
        assertThrows(IllegalArgumentException::class.java) {
            QuickActionProviderRegistry(setOf(FakeProvider("", setOf("a"), emptyList())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuickActionProviderRegistry(setOf(
                FakeProvider("a", setOf("same"), emptyList()),
                FakeProvider("b", setOf("same"), emptyList()),
            ))
        }
    }

    @Test fun `registry sorting and valid provider aggregation are deterministic`() = runBlocking {
        val sale = action(QuickActionIds.SALES_INVOICE, QuickActionDestinationIds.INVOICE_EDITOR, 200, "allow")
        val stock = action(QuickActionIds.QUICK_STOCK_COUNT, QuickActionDestinationIds.STOCK_COUNT, 100, "allow")
        val registry = QuickActionProviderRegistry(setOf(
            FakeProvider("z", setOf(sale.id), listOf(sale)),
            FakeProvider("a", setOf(stock.id), listOf(stock)),
        ))
        assertEquals(listOf("a", "z"), registry.all.map { it.providerId })
        assertEquals(listOf(stock.id, sale.id), ObserveQuickActionsUseCase(registry)(context).first().map { it.id })
    }

    @Test fun `aggregator rejects undeclared duplicate and wrong known destination`() = runBlocking {
        val undeclared = action("other", "custom", 1)
        val useCase = ObserveQuickActionsUseCase(QuickActionProviderRegistry(setOf(
            FakeProvider("p", setOf("declared"), listOf(undeclared)),
        )))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { useCase(context).first() } }

        val wrong = action(QuickActionIds.SALES_INVOICE, QuickActionDestinationIds.ADD_CLIENT, 1)
        val wrongUseCase = ObserveQuickActionsUseCase(QuickActionProviderRegistry(setOf(
            FakeProvider("p", setOf(wrong.id), listOf(wrong)),
        )))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { wrongUseCase(context).first() } }

        val duplicate = action("dup", "custom", 1)
        val duplicateUseCase = ObserveQuickActionsUseCase(QuickActionProviderRegistry(setOf(
            FakeProvider("p1", emptySet(), listOf(duplicate)),
            FakeProvider("p2", emptySet(), listOf(duplicate)),
        )))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { duplicateUseCase(context).first() } }
        Unit
    }

    @Test fun `permission filtering remains at aggregator boundary`() = runBlocking {
        val denied = action("denied", "custom", 1, "missing")
        val allowed = action("allowed", "custom", 2, "allow")
        val useCase = ObserveQuickActionsUseCase(QuickActionProviderRegistry(setOf(
            FakeProvider("p", setOf("denied", "allowed"), listOf(denied, allowed)),
        )))
        assertEquals(listOf("allowed"), useCase(context).first().map { it.id })
    }

    @Test fun `saved ordering preserves hidden slots and appends restored actions deterministically`() {
        val a = action("a", "custom", 1)
        val b = action("b", "custom", 2)
        val c = action("c", "custom", 3)
        assertEquals(listOf("b", "a", "c"), applySavedQuickActionOrder(listOf(a, b, c), listOf("b", "a")).map { it.id })
        assertEquals(
            listOf("b", "hidden", "a", "c"),
            mergeVisibleOrderIntoStoredOrder(
                previousStoredOrder = listOf("a", "hidden", "b"),
                orderedVisibleActionIds = listOf("b", "a", "c"),
                currentlyVisibleActionIds = setOf("a", "b", "c"),
            ),
        )
    }

    @Test fun `save order handles permission change and tenant scopes independently`() = runBlocking {
        val store = FakeOrderStore()
        val available = MutableStateFlow(listOf(action("a", "custom", 1), action("b", "custom", 2)))
        val provider = object : QuickActionProvider {
            override val providerId = "p"
            override val actionIds = setOf("a", "b", "c")
            override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> = available
        }
        val ordered = ObserveOrderedQuickActionsUseCase(
            ObserveQuickActionsUseCase(QuickActionProviderRegistry(setOf(provider))), store,
        )
        val scopeA = HomeStorageScope("org-a", "user-a")
        store.values[scopeA] = MutableStateFlow(listOf("a", "hidden", "b"))
        available.value = listOf(action("b", "custom", 2), action("c", "custom", 3))
        ordered.saveExplicitOrder(context, listOf("b", "a"), 10)
        assertEquals(listOf("a", "hidden", "b", "c"), store.values.getValue(scopeA).value)

        val other = HomePermissionContext("org-b", "user-a", setOf("allow"))
        ordered.saveExplicitOrder(other, listOf("c", "b"), 11)
        assertEquals(listOf("a", "hidden", "b", "c"), store.values.getValue(scopeA).value)
        assertEquals(listOf("c", "b"), store.values.getValue(HomeStorageScope("org-b", "user-a")).value)
    }

    @Test fun `save rejects blank and duplicate requested ids`() {
        val ordered = ObserveOrderedQuickActionsUseCase(
            ObserveQuickActionsUseCase(QuickActionProviderRegistry(emptySet())), FakeOrderStore(),
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ordered.saveExplicitOrder(context, listOf(""), 1) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ordered.saveExplicitOrder(context, listOf("a", "a"), 1) } }
    }

    private fun action(id: String, destination: String, order: Int, permission: String? = null) = QuickAction(
        id = id,
        label = id,
        destination = HomeDestination(destination),
        defaultOrder = order,
        requiredPermission = permission,
    )

    private class FakeProvider(
        override val providerId: String,
        override val actionIds: Set<String>,
        private val actions: List<QuickAction>,
    ) : QuickActionProvider {
        override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> = flowOf(actions)
    }

    private class FakeOrderStore : QuickActionOrderStore {
        val values = mutableMapOf<HomeStorageScope, MutableStateFlow<List<String>>>()
        override fun observeOrder(scope: HomeStorageScope): Flow<List<String>> =
            values.getOrPut(scope) { MutableStateFlow(emptyList()) }
        override suspend fun saveOrder(scope: HomeStorageScope, orderedActionIds: List<String>, updatedAtEpochMillis: Long) {
            values.getOrPut(scope) { MutableStateFlow(emptyList()) }.value = orderedActionIds
        }
        override suspend fun clearOrder(scope: HomeStorageScope) {
            values.getOrPut(scope) { MutableStateFlow(emptyList()) }.value = emptyList()
        }
    }
}
