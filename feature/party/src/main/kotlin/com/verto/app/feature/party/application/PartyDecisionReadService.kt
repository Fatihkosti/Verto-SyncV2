package com.verto.app.feature.party.application

import com.verto.app.feature.party.application.intelligence.CustomerCreditDecision
import com.verto.app.feature.party.application.intelligence.ObserveBestSupplierForItemUseCase
import com.verto.app.feature.party.application.intelligence.ObserveCustomerDecisionUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Narrow cross-feature read service. It exposes decisions, never Party persistence or payment commands. */
class PartyDecisionReadService @Inject constructor(
    private val customerDecision: ObserveCustomerDecisionUseCase,
    private val bestSupplierForItem: ObserveBestSupplierForItemUseCase,
) {
    fun observeCustomerDecisionGate(partyId: String): Flow<PartyCustomerDecisionReadModel> =
        customerDecision(partyId).map { snapshot ->
            PartyCustomerDecisionReadModel(
                gate = when (snapshot.creditDecision) {
                    CustomerCreditDecision.ALLOW_CREDIT -> PartyCreditGate.ALLOW_CREDIT
                    CustomerCreditDecision.CASH_ONLY -> PartyCreditGate.CASH_ONLY
                    CustomerCreditDecision.REQUIRES_APPROVAL -> PartyCreditGate.REQUIRES_APPROVAL
                },
                dataComplete = snapshot.dataComplete,
                reasons = snapshot.creditReasons,
                currentlyOverdueInvoiceCount = snapshot.currentlyOverdueInvoiceCount,
                maxCurrentDaysOverdue = snapshot.maxCurrentDaysOverdue,
                account = PartyCustomerAccountReadModel(
                    outstandingByCurrencyMinor = snapshot.outstandingByCurrencyMinor,
                    overdueByCurrencyMinor = snapshot.overdueByCurrencyMinor,
                ),
            )
        }

    fun observeSupplierRecommendation(inventoryItemId: String): Flow<PartySupplierRecommendationReadModel> =
        bestSupplierForItem(inventoryItemId).map { recommendation ->
            val best = recommendation.candidates.firstOrNull { it.supplierId == recommendation.bestSupplierId }
            PartySupplierRecommendationReadModel(
                inventoryItemId = recommendation.inventoryItemId,
                bestSupplierId = recommendation.bestSupplierId,
                bestSupplierScoreBps = best?.scoreBps,
                bestSupplierOrderCount = best?.orderCount,
                reasons = recommendation.reasons,
            )
        }
}
