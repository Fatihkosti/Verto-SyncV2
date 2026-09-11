package com.verto.app.feature.party.application.ledger

import com.verto.app.feature.party.domain.ledger.LedgerEvent
import com.verto.app.feature.party.domain.ledger.LedgerEventKey
import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.ledger.LedgerSide
import com.verto.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PartyLedgerEngineTest {
    private val engine = PartyLedgerEngine()

    @Test fun `sale and purchase remain separate for a dual role party`() {
        val events = listOf(event("sale", LedgerSide.CUSTOMER, 10_000), event("purchase", LedgerSide.SUPPLIER, 70_000))
        assertEquals(10_000, engine.build("p", LedgerSide.CUSTOMER, events, 0, 10).currencies.single().closing.amountMinor)
        assertEquals(70_000, engine.build("p", LedgerSide.SUPPLIER, events, 0, 10).currencies.single().closing.amountMinor)
    }

    @Test fun `opening plus period rows equals closing with exclusive upper boundary`() {
        val events = listOf(event("old", LedgerSide.CUSTOMER, 100, at = 1), event("payment", LedgerSide.CUSTOMER, -40, at = 5), event("later", LedgerSide.CUSTOMER, 9, at = 10))
        val result = engine.build("p", LedgerSide.CUSTOMER, events, 5, 10).currencies.single()
        assertEquals(100, result.opening.amountMinor)
        assertEquals(listOf(-40L), result.rows.map { it.event.delta.amountMinor })
        assertEquals(60, result.closing.amountMinor)
    }

    @Test fun `replay and payment derived credit are deduplicated`() {
        val payment = event("pay", LedgerSide.CUSTOMER, -125, type = LedgerEventType.PAYMENT)
        val projection = event("credit", LedgerSide.CUSTOMER, -25, derivedFrom = "pay")
        val result = engine.build("p", LedgerSide.CUSTOMER, listOf(payment, payment, projection), 0, 10)
        assertEquals(-125, result.currencies.single().closing.amountMinor)
    }

    @Test fun `mixed currencies are never combined and read order is irrelevant`() {
        val events = listOf(event("usd", LedgerSide.CUSTOMER, 100, currency = "USD"), event("sdg", LedgerSide.CUSTOMER, 200, currency = "SDG"))
        val a = engine.build("p", LedgerSide.CUSTOMER, events, 0, 10)
        val b = engine.build("p", LedgerSide.CUSTOMER, events.reversed(), 0, 10)
        assertEquals(listOf("SDG", "USD"), a.currencies.map { it.currencyCode })
        assertEquals(a, b)
    }

    @Test fun `void is a movement at void time rather than rewritten opening`() {
        val events = listOf(event("invoice", LedgerSide.CUSTOMER, 500, at = 1), event("void", LedgerSide.CUSTOMER, -500, at = 7, type = LedgerEventType.VOID))
        val ledger = engine.build("p", LedgerSide.CUSTOMER, events, 5, 9).currencies.single()
        assertEquals(500, ledger.opening.amountMinor)
        assertEquals(-500, ledger.rows.single().event.delta.amountMinor)
        assertEquals(0, ledger.closing.amountMinor)
    }

    private fun event(
        id: String,
        side: LedgerSide,
        minor: Long,
        at: Long = 2,
        currency: String = "SDG",
        type: LedgerEventType = LedgerEventType.INVOICE,
        derivedFrom: String? = null,
    ) = LedgerEvent(
        LedgerEventKey(type.name, id, "MAIN"), "p", side, type, Money.ofMinor(minor, currency),
        at, at, id, derivedFromPaymentId = derivedFrom,
    )
}
