package com.verto.app.feature.invoice.bridge

import com.verto.app.feature.invoice.domain.port.PurchaseSupplierRecommendation
import com.verto.app.feature.invoice.domain.port.PurchaseSupplierRecommendationPort
import com.verto.app.feature.party.application.PartyDecisionReadService
import com.verto.app.feature.party.application.PartySupplierRecommendationReadModel
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class PartyPurchaseSupplierRecommendationAdapter @Inject constructor(
    private val partyDecisionReadService: PartyDecisionReadService,
) : PurchaseSupplierRecommendationPort {
    override suspend fun recommendationForItem(inventoryItemId: String): PurchaseSupplierRecommendation {
        val recommendation = partyDecisionReadService.observeSupplierRecommendation(inventoryItemId)
            .catch {
                emit(
                    PartySupplierRecommendationReadModel(
                        inventoryItemId = inventoryItemId,
                        bestSupplierId = null,
                        bestSupplierScoreBps = null,
                        bestSupplierOrderCount = null,
                        reasons = listOf("INTELLIGENCE_UNAVAILABLE"),
                    )
                )
            }
            .first()
        return PurchaseSupplierRecommendation(
            inventoryItemId = recommendation.inventoryItemId,
            bestSupplierId = recommendation.bestSupplierId,
            scoreBps = recommendation.bestSupplierScoreBps,
            orderCount = recommendation.bestSupplierOrderCount,
        )
    }
}
