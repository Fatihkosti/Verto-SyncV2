package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.domain.repository.PartyFinancialQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveCustomerDecisionUseCase @Inject constructor(
    private val financial: PartyFinancialQuery,
) {
    internal operator fun invoke(
        partyId: String,
        policy: CustomerCreditPolicy = CustomerCreditPolicy(),
        nowProvider: () -> Long = System::currentTimeMillis,
    ): Flow<CustomerDecisionSnapshot> = financial.getInvoiceSummariesForClient(partyId).map { summaries ->
        CustomerDecisionEngine.evaluate(summaries = summaries, now = nowProvider(), policy = policy)
    }
}
