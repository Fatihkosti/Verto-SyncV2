package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.application.port.SupplierIntelligenceSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveSupplierPerformanceUseCase @Inject constructor(
    private val source: SupplierIntelligenceSource,
) {
    internal operator fun invoke(
        supplierId: String,
        policy: SupplierScorePolicy = SupplierScorePolicy(),
        nowProvider: () -> Long = System::currentTimeMillis,
    ): Flow<SupplierPerformanceSnapshot> =
        source.observeSupplierOrders(supplierId).map { orders ->
            SupplierIntelligenceEngine.evaluate(orders, nowProvider(), policy)
        }
}

class ObserveBestSupplierForItemUseCase @Inject constructor(
    private val source: SupplierIntelligenceSource,
) {
    internal operator fun invoke(
        inventoryItemId: String,
        policy: SupplierScorePolicy = SupplierScorePolicy(),
        nowProvider: () -> Long = System::currentTimeMillis,
    ): Flow<SupplierItemRecommendation> =
        source.observeItemOrders(inventoryItemId).map { orders ->
            SupplierIntelligenceEngine.recommendForItem(inventoryItemId, orders, nowProvider(), policy)
        }
}
