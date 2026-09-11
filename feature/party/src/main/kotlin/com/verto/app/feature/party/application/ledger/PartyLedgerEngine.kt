package com.verto.app.feature.party.application.ledger

import com.verto.app.feature.party.domain.ledger.CurrencyLedger
import com.verto.app.feature.party.domain.ledger.LedgerEvent
import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.ledger.LedgerRow
import com.verto.app.feature.party.domain.ledger.LedgerSide
import com.verto.app.feature.party.domain.ledger.PartyLedger
import com.verto.app.money.Money
import javax.inject.Inject

/** Pure, deterministic accounting engine. It never combines currencies or rewrites history. */
class PartyLedgerEngine @Inject constructor() {
    fun build(
        partyId: String,
        side: LedgerSide,
        events: Iterable<LedgerEvent>,
        fromInclusive: Long,
        toExclusive: Long,
    ): PartyLedger {
        require(partyId.isNotBlank())
        require(fromInclusive <= toExclusive)

        val selected = events.asSequence()
            .filter { it.partyId == partyId && it.side == side }
            .sortedWith(compareBy<LedgerEvent>({ it.recordedAt }, { it.eventId }))
            .distinctBy { it.key }
            .filterNot { event ->
                event.derivedFromPaymentId != null && events.any { candidate ->
                    candidate.type in setOf(LedgerEventType.PAYMENT, LedgerEventType.PAYMENT_REVERSAL) &&
                        candidate.key.sourceId == event.derivedFromPaymentId
                }
            }
            .sortedWith(compareBy<LedgerEvent>({ it.occurredAt }, { it.recordedAt }, { it.eventId }))
            .toList()

        val currencies = selected.groupBy { it.delta.currencyCode }.map { (currency, currencyEvents) ->
            val openingMinor = currencyEvents.asSequence()
                .filter { it.occurredAt < fromInclusive }
                .fold(0L) { total, event -> Math.addExact(total, event.delta.amountMinor) }
            var running = Money.ofMinor(openingMinor, currency)
            val rows = currencyEvents.asSequence()
                .filter { it.occurredAt >= fromInclusive && it.occurredAt < toExclusive }
                .map { event ->
                    running += event.delta
                    LedgerRow(event, running)
                }.toList()
            CurrencyLedger(currency, Money.ofMinor(openingMinor, currency), rows, running)
        }.sortedBy { it.currencyCode }

        return PartyLedger(partyId, side, fromInclusive, toExclusive, currencies)
    }
}
