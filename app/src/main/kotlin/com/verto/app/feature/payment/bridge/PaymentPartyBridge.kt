package com.verto.app.feature.payment.bridge

import com.verto.app.data.repository.ClientRepository
import com.verto.app.feature.party.application.PartyCreditGate
import com.verto.app.feature.party.application.PartyDecisionReadService
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.customerSegmentFromStorage
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.SupplierScope
import com.verto.app.feature.party.domain.repository.PartyRoleProjection
import com.verto.app.feature.party.domain.repository.PartyRoleProjectionGateway
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.PaymentCreditDecision
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision
import com.verto.app.feature.payment.application.model.PaymentSupplierRecommendation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-composition boundary for the Party reads/writes needed by the invoice editor. */
class PaymentPartyBridge @Inject constructor(
    private val roleProjectionGateway: PartyRoleProjectionGateway,
    private val partyRepository: ClientRepository,
    private val partyDecisionReadService: PartyDecisionReadService,
) {
    fun observeClients(): Flow<List<ClientItem>> =
        roleProjectionGateway.observeRoleProjections().map { list -> list.map(PartyRoleProjection::toPaymentView) }

    suspend fun insertClient(client: ClientItem) {
        val party = client.toPartyClient()
        val customerProfile = client.customerSegment
            ?.let(::customerSegmentFromStorage)
            ?.let { CustomerProfile(partyId = client.id, segment = it) }
        val supplierProfile = client.supplierScope
            ?.let { runCatching { SupplierScope.valueOf(it) }.getOrNull() }
            ?.let { SupplierProfile(partyId = client.id, scope = it) }
        require(customerProfile != null || supplierProfile != null) { "Party V2 role profile required" }
        partyRepository.insertParty(party, customerProfile, supplierProfile).getOrThrow()
    }

    fun observeCustomerDecision(clientId: String): Flow<PaymentCustomerDecision> =
        partyDecisionReadService.observeCustomerDecisionGate(clientId).map { decision ->
            PaymentCustomerDecision(
                decision = when (decision.gate) {
                    PartyCreditGate.ALLOW_CREDIT -> PaymentCreditDecision.ALLOW_CREDIT
                    PartyCreditGate.CASH_ONLY -> PaymentCreditDecision.CASH_ONLY
                    PartyCreditGate.REQUIRES_APPROVAL -> PaymentCreditDecision.REQUIRES_APPROVAL
                },
                dataComplete = decision.dataComplete,
                reasons = decision.reasons,
                currentlyOverdueInvoiceCount = decision.currentlyOverdueInvoiceCount,
                maxCurrentDaysOverdue = decision.maxCurrentDaysOverdue,
                account = com.verto.app.feature.payment.application.model.PaymentCustomerAccountSnapshot(
                    outstandingByCurrencyMinor = decision.account.outstandingByCurrencyMinor,
                    overdueByCurrencyMinor = decision.account.overdueByCurrencyMinor,
                ),
            )
        }

    fun observeSupplierRecommendation(inventoryItemId: String): Flow<PaymentSupplierRecommendation> =
        partyDecisionReadService.observeSupplierRecommendation(inventoryItemId).map { recommendation ->
            PaymentSupplierRecommendation(
                inventoryItemId = recommendation.inventoryItemId,
                bestSupplierId = recommendation.bestSupplierId,
                bestSupplierScoreBps = recommendation.bestSupplierScoreBps,
                bestSupplierOrderCount = recommendation.bestSupplierOrderCount,
                reasons = recommendation.reasons,
            )
        }

}
