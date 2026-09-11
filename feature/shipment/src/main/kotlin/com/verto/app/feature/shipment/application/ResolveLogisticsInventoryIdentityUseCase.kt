package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsInventoryCatalogItem
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryIdentityPort
import com.verto.app.utils.SearchTextNormalizer
import javax.inject.Inject

enum class LogisticsInventoryIdentitySource { EXACT_INVENTORY_ITEM_ID, UNIQUE_NORMALIZED_NAME }

sealed interface LogisticsInventoryIdentityResolution {
    data class Resolved(
        val inventoryItemId: String,
        val source: LogisticsInventoryIdentitySource,
    ) : LogisticsInventoryIdentityResolution

    data class Ambiguous(
        val normalizedName: String,
        val candidates: List<LogisticsInventoryCatalogItem>,
    ) : LogisticsInventoryIdentityResolution

    data class Missing(val normalizedName: String) : LogisticsInventoryIdentityResolution
}

/**
 * Fail-closed resolver for shipment receiving identity. A display name is never accepted when
 * normalization maps it to more than one catalog row.
 */
object LogisticsInventoryIdentityResolver {
    fun resolve(
        explicitInventoryItemId: String,
        itemName: String,
        catalog: List<LogisticsInventoryCatalogItem>,
    ): LogisticsInventoryIdentityResolution {
        val explicitId = explicitInventoryItemId.trim()
        catalog.firstOrNull { it.id == explicitId }?.let {
            return LogisticsInventoryIdentityResolution.Resolved(
                inventoryItemId = it.id,
                source = LogisticsInventoryIdentitySource.EXACT_INVENTORY_ITEM_ID,
            )
        }

        val normalizedName = SearchTextNormalizer.text(itemName)
        if (normalizedName.isBlank()) return LogisticsInventoryIdentityResolution.Missing(normalizedName)
        val candidates = catalog.filter { SearchTextNormalizer.text(it.name) == normalizedName }
        return when (candidates.size) {
            0 -> LogisticsInventoryIdentityResolution.Missing(normalizedName)
            1 -> LogisticsInventoryIdentityResolution.Resolved(
                inventoryItemId = candidates.single().id,
                source = LogisticsInventoryIdentitySource.UNIQUE_NORMALIZED_NAME,
            )
            else -> LogisticsInventoryIdentityResolution.Ambiguous(normalizedName, candidates)
        }
    }
}

class ResolveLogisticsInventoryIdentityUseCase @Inject constructor(
    private val identities: LogisticsInventoryIdentityPort,
) {
    suspend fun catalog(): List<LogisticsInventoryCatalogItem> = identities.listCatalog()

    suspend fun bindExisting(invoiceItemId: String, inventoryItemId: String): String {
        require(invoiceItemId.isNotBlank()) { "invoiceItemId is required" }
        require(inventoryItemId.isNotBlank()) { "inventoryItemId is required" }
        return identities.bindInvoiceLine(invoiceItemId, inventoryItemId)
    }

    suspend fun createNew(invoiceItemId: String): String {
        require(invoiceItemId.isNotBlank()) { "invoiceItemId is required" }
        return identities.createZeroStockAndBind(invoiceItemId)
    }
}
