package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientStatus
import com.verto.app.feature.party.domain.model.PartyInvoice
import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import com.verto.app.feature.party.domain.model.PartyInvoiceStatus
import com.verto.app.feature.party.domain.model.PartyInvoiceType
import com.verto.app.feature.party.domain.model.PartyPayment
import com.verto.app.feature.party.domain.model.PartyPaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateClientBalanceUseCaseTest {
    private val useCase = CalculateClientBalanceUseCase()

    @Test fun `sale debt minus payments produces remaining balance`() {
        val client = client()
        val invoice = invoice("i", client.id, 100.0, true, Long.MAX_VALUE)
        val result = useCase(client, listOf(invoice), listOf(payment("p", "i", client.id, 40.0)))
        assertEquals(60.0, result.remaining, 0.0)
        assertEquals(40.0, result.totalPaid, 0.0)
        assertEquals(PartyClientStatus.GREEN, result.status)
    }

    @Test fun `overdue positive receivable is red`() {
        val client = client()
        val result = useCase(client, listOf(invoice("i", client.id, 100.0, true, 1L)), emptyList())
        assertEquals(PartyClientStatus.RED, result.status)
    }

    @Test fun `fully paid balance is grey`() {
        val client = client()
        val invoice = invoice("i", client.id, 100.0, true, 1L)
        val result = useCase(client, listOf(invoice), listOf(payment("p", "i", client.id, 100.0)))
        assertEquals(PartyClientStatus.GREY, result.status)
        assertEquals(0.0, result.remaining, 0.0)
    }

    @Test fun `supplier payable subtracts from net balance`() {
        val client = client()
        val result = useCase(client, listOf(invoice("i", client.id, 75.0, false, 0L)), emptyList())
        assertEquals(-75.0, result.remaining, 0.0)
    }

    @Test fun `records for other clients are ignored`() {
        val client = client()
        val result = useCase(client, listOf(invoice("i", "other", 999.0, true, 1L)), emptyList())
        assertEquals(0.0, result.remaining, 0.0)
    }

    @Test fun `multiple invoices and payments aggregate deterministically`() {
        val client = client()
        val invoices = listOf(
            invoice("i1", client.id, 100.0, true, Long.MAX_VALUE),
            invoice("i2", client.id, 50.0, true, Long.MAX_VALUE),
        )
        val payments = listOf(
            payment("p1", "i1", client.id, 20.0),
            payment("p2", "i1", client.id, 10.0),
            payment("p3", "i2", client.id, 25.0),
        )
        val result = useCase(client, invoices, payments)
        assertEquals(95.0, result.remaining, 0.0)
        assertEquals(55.0, result.totalPaid, 0.0)
        assertEquals(150.0, result.totalDebt, 0.0)
    }

    private fun client() = PartyClient(id = "c", name = "Client", phone = "249")

    private fun invoice(id: String, clientId: String, total: Double, owed: Boolean, due: Long) = PartyInvoice(
        id = id,
        invoiceNumber = 1,
        clientId = clientId,
        type = PartyInvoiceType.GOODS,
        category = PartyInvoiceCategory.SALE,
        description = "x",
        totalAmount = total,
        createdAt = 0L,
        dueDate = due,
        isOwedToMe = owed,
        status = PartyInvoiceStatus.CLOSED_CREDIT,
    )

    private fun payment(id: String, invoiceId: String, clientId: String, amount: Double) = PartyPayment(
        id = id,
        invoiceId = invoiceId,
        clientId = clientId,
        amount = amount,
        paymentMethod = PartyPaymentMethod.CASH,
        paidAt = 0L,
    )
}
