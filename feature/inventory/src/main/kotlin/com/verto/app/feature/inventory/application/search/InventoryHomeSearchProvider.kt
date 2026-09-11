package com.verto.app.feature.inventory.application.search

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.utils.SearchTextNormalizer
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import javax.inject.Inject

data class InventoryHomeSearchRecord(
    val id: String,
    val name: String,
    val partNumber: String,
    val barcode: String,
    val quantity: Int,
    val isService: Boolean,
    val updatedAtEpochMillis: Long,
)

interface InventoryHomeSearchSource {
    suspend fun search(
        organizationId: String,
        textQuery: String,
        identifierQuery: String,
        limit: Int,
    ): List<InventoryHomeSearchRecord>
}

class InventoryHomeSearchProvider @Inject constructor(
    private val source: InventoryHomeSearchSource,
    private val sessionReader: SessionReader,
) : HomeSearchProvider {
    override val providerId: String = "inventory.business"

    override suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext,
    ): List<HomeSearchResult> {
        if (!context.allows(HomePermissionKeys.INVENTORY_VIEW) || !context.matchesActiveSession()) {
            return emptyList()
        }
        val keys = SearchTextNormalizer.query(query.text) ?: return emptyList()
        return source.search(context.organizationId, keys.text, keys.identifier, query.limit).map { record ->
            val details = HomeDestination(
                id = HomeDestinationIds.INVENTORY_ITEM_DETAILS,
                arguments = mapOf("itemId" to record.id),
            )
            val actions = buildList {
                add(HomeAction("open_inventory_item", "فتح", details, HomePermissionKeys.INVENTORY_VIEW))
                if (context.allows(HomePermissionKeys.INVENTORY_EDIT)) {
                    add(
                        HomeAction(
                            id = "edit_inventory_item",
                            label = "تعديل",
                            destination = HomeDestination(
                                id = HomeDestinationIds.INVENTORY_ITEM_EDIT,
                                arguments = mapOf("itemId" to record.id),
                            ),
                            requiredPermission = HomePermissionKeys.INVENTORY_EDIT,
                        ),
                    )
                }
            }
            HomeSearchResult(
                key = record.id,
                title = record.name,
                subtitle = record.subtitle(),
                kind = HomeSearchKind.INVENTORY_ITEM,
                destination = details,
                actions = actions,
                requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
                updatedAtEpochMillis = record.updatedAtEpochMillis,
            )
        }
    }

    private fun InventoryHomeSearchRecord.subtitle(): String = buildList {
        if (partNumber.isNotBlank()) add("رقم $partNumber")
        if (barcode.isNotBlank()) add("باركود $barcode")
        add(if (isService) "خدمة" else "الكمية $quantity")
    }.joinToString(" • ")

    private suspend fun HomePermissionContext.matchesActiveSession(): Boolean {
        val session = sessionReader.snapshot()
        return organizationId == session.organization.id && userId == session.user.id
    }
}
