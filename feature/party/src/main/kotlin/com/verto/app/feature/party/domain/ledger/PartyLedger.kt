package com.verto.app.feature.party.domain.ledger

import com.verto.app.money.Money

enum class LedgerSide { CUSTOMER, SUPPLIER }

enum class LedgerEventType {
    INVOICE,
    CASH_SETTLEMENT,
    PAYMENT,
    PAYMENT_REVERSAL,
    MANUAL_CREDIT,
    CREDIT_REVERSAL,
    VOID,
    RETURN,
}

data class LedgerEventKey(
    val sourceType: String,
    val sourceId: String,
    val component: String,
) {
    init {
        require(sourceType.isNotBlank())
        require(sourceId.isNotBlank())
        require(component.isNotBlank())
    }
}

/** [delta] is signed from the selected ledger's perspective: debt +, settlement -. */
data class LedgerEvent(
    val key: LedgerEventKey,
    val partyId: String,
    val side: LedgerSide,
    val type: LedgerEventType,
    val delta: Money,
    val occurredAt: Long,
    val recordedAt: Long,
    val eventId: String,
    val description: String = "",
    /** A compatibility projection is excluded when its originating payment is present. */
    val derivedFromPaymentId: String? = null,
) {
    init {
        require(partyId.isNotBlank())
        require(occurredAt >= 0L)
        require(recordedAt >= 0L)
        require(eventId.isNotBlank())
    }
}

data class LedgerRow(
    val event: LedgerEvent,
    val runningBalance: Money,
)

data class CurrencyLedger(
    val currencyCode: String,
    val opening: Money,
    val rows: List<LedgerRow>,
    val closing: Money,
)

data class PartyLedger(
    val partyId: String,
    val side: LedgerSide,
    val fromInclusive: Long,
    val toExclusive: Long,
    val currencies: List<CurrencyLedger>,
) {
    init {
        require(fromInclusive <= toExclusive)
    }
}
