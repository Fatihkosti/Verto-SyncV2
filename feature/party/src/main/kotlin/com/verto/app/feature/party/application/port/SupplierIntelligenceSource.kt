package com.verto.app.feature.party.application.port

import kotlinx.coroutines.flow.Flow

/**
 * Party-owned read contract. The app composition layer maps purchase persistence into these facts,
 * keeping the supplier decision engine independent from Invoice/Room implementation types.
 */
interface SupplierIntelligenceSource {
    fun observeSupplierOrders(supplierId: String): Flow<List<SupplierOrderEvidence>>
    fun observeItemOrders(inventoryItemId: String): Flow<List<SupplierOrderEvidence>>
}
