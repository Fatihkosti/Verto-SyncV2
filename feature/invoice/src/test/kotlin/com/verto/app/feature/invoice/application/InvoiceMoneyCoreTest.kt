package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceDraftItem
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceMoneyCoreTest {
    private val validator = InvoiceSaveValidator()

    @Test fun `local purchase supplier total uses buy price only`() {
        val validated = validator.validate(purchase(quantity = "2", buy = "100", sell = "150"))
        assertEquals(20_000L, validated.total.amountMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid quantity never falls back to one`() {
        validator.validate(purchase(quantity = "abc", buy = "100", sell = "150"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank buy price is rejected for local purchase`() {
        validator.validate(purchase(quantity = "2", buy = "", sell = "150"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero buy price is rejected for local purchase`() {
        validator.validate(purchase(quantity = "2", buy = "0", sell = "150"))
    }

    @Test fun `decimal multiplication is exact`() {
        val validated = validator.validate(purchase(quantity = "3", buy = "0.1", sell = "1"))
        assertEquals(30L, validated.total.amountMinor)
    }

    @Test fun `sale uses sell price and ignores buy price for invoice revenue`() {
        val command = SaveInvoiceCommand(
            existingInvoiceId = null,
            clientId = "client",
            items = listOf(InvoiceDraftItem(name = "Part", quantity = "2", buyPrice = "100", sellPrice = "150")),
            paymentMode = InvoicePaymentMode.CASH,
            dueDate = 0,
            notes = "",
            isSale = true,
        )
        assertEquals(30_000L, validator.validate(command).total.amountMinor)
    }

    private fun purchase(quantity: String, buy: String, sell: String) = SaveInvoiceCommand(
        existingInvoiceId = null,
        clientId = "supplier",
        items = listOf(InvoiceDraftItem(name = "Part", quantity = quantity, buyPrice = buy, sellPrice = sell)),
        paymentMode = InvoicePaymentMode.CASH,
        dueDate = 0,
        notes = "",
        isSale = false,
    )
}
