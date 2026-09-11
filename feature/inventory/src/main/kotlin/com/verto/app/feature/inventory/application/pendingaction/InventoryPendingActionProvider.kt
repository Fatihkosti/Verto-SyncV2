package com.verto.app.feature.inventory.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionDetailField
import com.verto.feature.dashboard.api.PendingActionDetailRow
import com.verto.feature.dashboard.api.PendingActionDetails
import com.verto.feature.dashboard.api.PendingActionEventKey
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionSection
import com.verto.feature.dashboard.api.PendingActionProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

interface InventoryPendingActionSource {
    fun observeItems(
        organizationId: String,
        staleCutoffEpochMillis: Long,
    ): Flow<List<InventoryPendingItemRecord>>
    fun observePriceBatches(organizationId: String): Flow<List<InventoryPriceBatchRecord>>
}

interface InventoryPendingActionClock {
    fun observeNowEpochMillis(): Flow<Long>
}

class InventoryPendingActionProvider @Inject constructor(
    private val source: InventoryPendingActionSource,
    private val clock: InventoryPendingActionClock,
    private val sessionReader: SessionReader,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id) {
            emit(emptyList())
            return@flow
        }

        val canViewInventory = context.allows(HomePermissionKeys.INVENTORY_VIEW)
        val canViewPrices = context.allows(HomePermissionKeys.INVENTORY_PRICE)
        if (!canViewInventory && !canViewPrices) {
            emit(emptyList())
            return@flow
        }

        val inventoryEvents = if (canViewInventory) {
            clock.observeNowEpochMillis().flatMapLatest { now ->
                require(now >= 0L) { "nowEpochMillis must not be negative" }
                val staleCutoff = (now - STALE_AFTER_MS).coerceAtLeast(0L)
                source.observeItems(context.organizationId, staleCutoff).map { items ->
                    buildList {
                        addAll(buildInventoryStockStatusEvents(context, items))
                        buildStaleInventoryEvent(context, items, now)?.let(::add)
                    }
                }
            }
        } else {
            flowOf(emptyList())
        }

        val priceEvents = if (canViewPrices) {
            source.observePriceBatches(context.organizationId).map { batches ->
                batches.asSequence()
                    .filter { it.itemCount > 1 }
                    .map { it.priceBatchEvent() }
                    .toList()
            }
        } else {
            flowOf(emptyList())
        }

        emitAll(
            combine(inventoryEvents, priceEvents) { inventory, prices ->
                (inventory + prices)
                    .sortedWith(PROVIDER_RANKING)
            },
        )
    }

    private fun buildStaleInventoryEvent(
        context: HomePermissionContext,
        items: List<InventoryPendingItemRecord>,
        nowEpochMillis: Long,
    ): PendingAction? {
        val staleItems = items.asSequence()
            .filterNot(InventoryPendingItemRecord::isService)
            .filter { it.quantity > 0 }
            .mapNotNull { item ->
                val reliableReference = item.lastSaleAtEpochMillis ?: item.createdAtEpochMillis
                val staleAt = reliableReference + STALE_AFTER_MS
                if (reliableReference < 0L || staleAt > nowEpochMillis) null else item to staleAt
            }
            .sortedBy { (item, _) -> item.itemName }
            .toList()
        if (staleItems.isEmpty()) return null

        val destination = HomeDestination(id = HomeDestinationIds.INVENTORY_ITEM_DETAILS)
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_STALE_ITEMS, context.organizationId)
        return PendingAction(
            eventKey = eventKey,
            title = "أصناف راكدة",
            summary = "${staleItems.size} صنف بلا حركة بيع منذ 90 يومًا أو أكثر",
            occurredAtEpochMillis = staleItems.minOf { (_, staleAt) -> staleAt.coerceAtLeast(0L) },
            priority = PendingActionPriority.NORMAL,
            destination = destination,
            actions = listOf(
                HomeAction(
                    id = ACTION_OPEN_STALE_ITEMS,
                    label = "عرض الأصناف",
                    destination = destination,
                    requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
                ),
            ),
            details = PendingActionDetails(
                title = "الأصناف الراكدة",
                rows = staleItems.map { (item, _) ->
                    PendingActionDetailRow(
                        title = item.itemName,
                        fields = listOf(
                            PendingActionDetailField("الكمية الحالية", item.quantity.toString()),
                            PendingActionDetailField("الحالة", "راكد 90 يومًا أو أكثر"),
                        ),
                    )
                },
            ),
            section = PendingActionSection.INVENTORY,
            requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
        )
    }

    private fun InventoryPriceBatchRecord.priceBatchEvent(): PendingAction {
        val destination = HomeDestination(id = BULK_PRICE_DESTINATION_ID)
        return PendingAction(
            eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_PRICE_BATCH, batchId),
            title = "تعديل أسعار مجموعة أصناف",
            summary = "تم تحديث أسعار $itemCount أصناف في عملية واحدة",
            occurredAtEpochMillis = occurredAtEpochMillis.coerceAtLeast(0L),
            priority = PendingActionPriority.LOW,
            destination = destination,
            actions = listOf(
                HomeAction(
                    id = "open_bulk_price",
                    label = "فتح",
                    destination = destination,
                    requiredPermission = HomePermissionKeys.INVENTORY_PRICE,
                ),
            ),
            section = PendingActionSection.OTHER,
            requiredPermission = HomePermissionKeys.INVENTORY_PRICE,
        )
    }



    private val PROVIDER_RANKING: Comparator<PendingAction> =
        compareByDescending<PendingAction> { action -> action.priority.localRank }
            .thenBy { action -> action.occurredAtEpochMillis }
            .thenBy { action -> action.eventKey }

    private val PendingActionPriority.localRank: Int
        get() = when (this) {
            PendingActionPriority.LOW -> 0
            PendingActionPriority.NORMAL -> 1
            PendingActionPriority.HIGH -> 2
            PendingActionPriority.CRITICAL -> 3
        }

    companion object {
        const val PROVIDER_ID = InventoryPendingActionKeys.PROVIDER_ID
        const val EVENT_PRICE_BATCH = "price_batch"
        const val EVENT_STALE_ITEMS = "stale_items_90_days"
        const val ACTION_OPEN_STALE_ITEMS = "open_stale_items"
        const val BULK_PRICE_DESTINATION_ID = "action.bulk_price_edit"
        const val STALE_AFTER_MS: Long = 90L * 86_400_000L
    }
}
