package com.verto.app.feature.party.application.ledger

import com.verto.app.feature.party.domain.ledger.LedgerEvent
import com.verto.app.feature.party.domain.ledger.LedgerSide
import com.verto.app.feature.party.domain.ledger.PartyLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface PartyLedgerEventSource {
    fun observeEvents(partyId: String): Flow<List<LedgerEvent>>
}

class ObservePartyLedgerUseCase @Inject constructor(
    private val source: PartyLedgerEventSource,
    private val engine: PartyLedgerEngine = PartyLedgerEngine(),
) {
    operator fun invoke(
        partyId: String,
        side: LedgerSide,
        fromInclusive: Long = 0L,
        toExclusive: Long = Long.MAX_VALUE,
    ): Flow<PartyLedger> = source.observeEvents(partyId).map { events ->
        engine.build(partyId, side, events, fromInclusive, toExclusive)
    }
}

class ObserveCustomerLedgerUseCase @Inject constructor(private val ledger: ObservePartyLedgerUseCase) {
    operator fun invoke(partyId: String, fromInclusive: Long = 0L, toExclusive: Long = Long.MAX_VALUE) =
        ledger(partyId, LedgerSide.CUSTOMER, fromInclusive, toExclusive)
}

class ObserveSupplierLedgerUseCase @Inject constructor(private val ledger: ObservePartyLedgerUseCase) {
    operator fun invoke(partyId: String, fromInclusive: Long = 0L, toExclusive: Long = Long.MAX_VALUE) =
        ledger(partyId, LedgerSide.SUPPLIER, fromInclusive, toExclusive)
}
