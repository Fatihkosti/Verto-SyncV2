package com.verto.app.feature.inventory.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryPendingActionProvider337Test {
    @Test
    fun deniedPermissions_doNotCollectInventorySourcesOrClock() = runBlocking {
        val source = CountingSource()
        val clock = CountingClock()
        val provider = InventoryPendingActionProvider(source, clock, session())

        assertTrue(provider.observePendingActions(context()).first().isEmpty())
        assertEquals(0, source.itemCollections)
        assertEquals(0, source.priceCollections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun priceOnlyPermission_doesNotCollectInventoryItemSourceOrClock() = runBlocking {
        val source = CountingSource()
        val clock = CountingClock()
        val provider = InventoryPendingActionProvider(source, clock, session())

        provider.observePendingActions(context(setOf(HomePermissionKeys.INVENTORY_PRICE))).first()
        assertEquals(0, source.itemCollections)
        assertEquals(1, source.priceCollections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun stockStatuses_areGroupedPerCondition_notPerItem() {
        val events = buildInventoryStockStatusEvents(
            context = context(setOf(HomePermissionKeys.INVENTORY_VIEW)),
            items = listOf(
                item("out-1", "نافد أ", quantity = 0, minQuantity = 3),
                item("out-2", "نافد ب", quantity = -1, minQuantity = 4),
                item("low-1", "منخفض أ", quantity = 2, minQuantity = 3),
                item("ok-1", "متوفر", quantity = 10, minQuantity = 3),
            ),
        )

        assertEquals(listOf("أصناف نافدة تمامًا", "أصناف أوشكت على النفاد"), events.map { it.title })
        assertEquals(listOf(2, 1), events.map { it.details?.rows?.size })
        assertTrue(events.all { it.section == com.verto.feature.dashboard.api.PendingActionSection.INVENTORY })
    }

    @Test
    fun wrongTenant_doesNotCollectAnySource() = runBlocking {
        val source = CountingSource()
        val clock = CountingClock()
        val provider = InventoryPendingActionProvider(source, clock, session())

        assertTrue(provider.observePendingActions(context(setOf(HomePermissionKeys.INVENTORY_VIEW), org = "org-b")).first().isEmpty())
        assertEquals(0, source.itemCollections + source.priceCollections + clock.collections)
    }

    private class CountingSource : InventoryPendingActionSource {
        var itemCollections = 0
        var priceCollections = 0
        override fun observeItems(organizationId: String, staleCutoffEpochMillis: Long): Flow<List<InventoryPendingItemRecord>> = flow {
            itemCollections++
            emit(emptyList())
        }
        override fun observePriceBatches(organizationId: String): Flow<List<InventoryPriceBatchRecord>> = flow {
            priceCollections++
            emit(emptyList())
        }
    }

    private class CountingClock : InventoryPendingActionClock {
        var collections = 0
        override fun observeNowEpochMillis(): Flow<Long> = flow {
            collections++
            emit(1_000_000_000_000L)
        }
    }

    private fun item(
        id: String,
        name: String,
        quantity: Int,
        minQuantity: Int,
    ) = InventoryPendingItemRecord(
        itemId = id,
        itemName = name,
        quantity = quantity,
        minQuantity = minQuantity,
        isService = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        lastSaleAtEpochMillis = null,
    )

    private fun context(permissions: Set<String> = emptySet(), org: String = "org-a") =
        HomePermissionContext(org, "user-a", permissions)

    private fun session() = FakeSessionReader()

    private class FakeSessionReader : SessionReader {
        private val user = CurrentUser("user-a", "User", "")
        private val org = CurrentOrganization("org-a")
        override val userId = flowOf(user.id)
        override val userName = flowOf(user.name)
        override val userPhone = flowOf(user.phone)
        override val role = flowOf("admin")
        override val permissionsJson = flowOf("")
        override val organizationId = flowOf(org.id)
        override val currentUser = flowOf(user)
        override val currentOrganization = flowOf(org)
        override suspend fun snapshot() = SessionState(user, org, "admin", "")
    }
}
