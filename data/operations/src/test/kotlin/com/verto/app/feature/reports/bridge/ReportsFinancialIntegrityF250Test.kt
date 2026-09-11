package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsFinancialIntegrityF250Test {

    @Test
    fun mixedTransactionCurrencies_sumOnlyStoredFunctionalSnapshots() {
        val usd = invoice(
            id = "usd",
            transactionCurrency = "USD",
            transactionMinor = 10_000L,
            functionalCurrency = "SDG",
            functionalMinor = 25_000_000L,
        )
        val sdg = invoice(
            id = "sdg",
            transactionCurrency = "SDG",
            transactionMinor = 50_000_000L,
            functionalCurrency = "SDG",
            functionalMinor = 50_000_000L,
        )

        val currency = selectFunctionalCurrency(listOf(usd, sdg))
        val total = sumMinor(listOfNotNull(usd.functionalMinorOrNull(currency), sdg.functionalMinorOrNull(currency)))

        assertEquals("SDG", currency)
        assertEquals(75_000_000L, total)
        assertEquals(750_000.0, major(total, currency), 0.001)
    }

    @Test
    fun healthyLedger_hasZeroReconciliationDifferences() {
        val inv = invoice(id = "inv", transactionMinor = 10_000L, functionalMinor = 10_000L)
        val payment = PaymentEntity(
            id = "pay", invoiceId = inv.id, clientId = inv.clientId, amount = 100.0,
            amountMinor = 10_000L, paymentCurrencyCode = "SDG", supplierAmountMinor = 10_000L,
            functionalCashAmountMinor = 10_000L, historicalFunctionalAmountMinor = 10_000L,
            legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN, paymentMethod = PaymentMethod.CASH,
            writeId = "pay-write",
        )
        val allocation = PaymentAllocationEntity(
            id = "alloc", paymentId = payment.id, invoiceId = inv.id,
            allocatedTransactionAmountMinor = 10_000L,
            historicalFunctionalAmountMinor = 10_000L,
            realizedFxDifferenceMinor = 0L,
        )
        val item = InventoryItemEntity(id = "item", name = "قطعة", buyPrice = 10.0, buyPriceMinor = 1_000L, quantity = 5)
        val move = InventoryMovementEntity(
            id = "move", itemId = item.id, movementType = MovementType.IN,
            quantity = 5, quantityBefore = 0, quantityAfter = 5,
            unitPrice = 10.0, unitPriceMinor = 1_000L,
        )
        val cashMove = CashRegisterMovementEntity(
            id = "cash", movementType = CashMovementType.SALE_CASH, amount = 100.0, amountMinor = 10_000L,
            balanceBefore = 0.0, balanceBeforeMinor = 0L, balanceAfter = 100.0, balanceAfterMinor = 10_000L,
            writeId = "invoice-write",
        )
        val guard = InvoiceWriteGuardEntity(
            id = "guard", organizationId = "org", operationType = "POST_INVOICE", writeId = "invoice-write", targetInvoiceId = inv.id,
            createdAt = 2L,
        )
        val outbox = FinancialOutboxEntity(
            eventId = "event", organizationId = "org", writeId = "invoice-write", aggregateId = inv.id,
            aggregateVersion = 1, sequence = 1L, operationType = "POST_INVOICE", payload = "{}", occurredAt = 1L,
            recordedAt = 1L, createdAt = 1L, syncState = "ACKNOWLEDGED",
        )

        val result = buildFinancialDiagnostics(
            organizationId = "org", invoices = listOf(inv), invoiceItems = emptyList(), payments = listOf(payment),
            allocations = listOf(allocation), cashRegister = CashRegisterEntity(balance = 100.0, balanceMinor = 10_000L),
            cashMovements = listOf(cashMove), inventoryItems = listOf(item), inventoryMovements = listOf(move),
            outbox = listOf(outbox), writeGuards = listOf(guard),
        )

        assertTrue(result.checks.all { it.differenceMinor == 0L && it.affectedCount == 0 })
        assertEquals(5_000L, result.inventoryOperationalValueMinor)
        assertEquals(0, result.outboxRequiresReviewCount)
    }

    @Test
    fun intentionalCorruption_isReported() {
        val inv = invoice(id = "inv", transactionMinor = 10_000L, functionalMinor = 10_000L)
        val payment = PaymentEntity(
            id = "pay", invoiceId = inv.id, clientId = inv.clientId, amount = 100.0,
            amountMinor = 10_000L, paymentCurrencyCode = "SDG", supplierAmountMinor = 10_000L,
            functionalCashAmountMinor = 10_000L, historicalFunctionalAmountMinor = 10_000L,
            legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN, paymentMethod = PaymentMethod.CASH,
        )
        val corruptAllocation = PaymentAllocationEntity(
            id = "alloc", paymentId = payment.id, invoiceId = inv.id,
            allocatedTransactionAmountMinor = 12_000L,
            historicalFunctionalAmountMinor = 12_000L,
            realizedFxDifferenceMinor = 0L,
        )

        val result = buildFinancialDiagnostics(
            organizationId = "org", invoices = listOf(inv), invoiceItems = emptyList(), payments = listOf(payment),
            allocations = listOf(corruptAllocation), cashRegister = null, cashMovements = emptyList(),
            inventoryItems = emptyList(), inventoryMovements = emptyList(), outbox = emptyList(), writeGuards = emptyList(),
        )

        assertFalse(result.isHealthy)
        assertEquals(2_000L, result.checks.first { it.code == "INVOICE_ALLOCATION" }.differenceMinor)
        assertEquals(2_000L, result.checks.first { it.code == "PAYMENT_ALLOCATION" }.differenceMinor)
    }

    private fun invoice(
        id: String,
        transactionCurrency: String = "SDG",
        transactionMinor: Long = 10_000L,
        functionalCurrency: String = "SDG",
        functionalMinor: Long = transactionMinor,
    ) = InvoiceEntity(
        id = id,
        invoiceNumber = 1,
        clientId = "client",
        organizationId = "org",
        type = InvoiceType.GOODS,
        category = InvoiceCategory.SALE,
        description = "",
        totalAmount = transactionMinor / 100.0,
        totalAmountMinor = transactionMinor,
        transactionCurrencyCode = transactionCurrency,
        functionalCurrencyCode = functionalCurrency,
        transactionAmountMinor = transactionMinor,
        functionalAmountAtRecognitionMinor = functionalMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        dueDate = 0L,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED,
    )
}
