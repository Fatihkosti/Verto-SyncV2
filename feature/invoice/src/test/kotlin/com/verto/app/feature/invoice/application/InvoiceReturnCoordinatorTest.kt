package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.AuditRecord
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.feature.invoice.domain.model.InvoiceCategory as DomainInvoiceCategory
import com.verto.app.feature.invoice.domain.model.*
import com.verto.app.feature.invoice.domain.port.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InvoiceReturnCoordinatorTest {
    @Test
    fun `sale return is additive, partial retries cannot exceed source, and buy price is untouched`() = runTest {
        val original = invoice(DomainInvoiceCategory.SALE)
        val line = saleLine(quantity = 3)
        val f = Fixture(original, listOf(line))
        val before = f.invoices.invoice

        assertTrue(f.coordinator.create(command("r1", 1)).isSuccess)
        assertTrue(f.coordinator.create(command("r2", 2)).isSuccess)
        assertTrue(f.coordinator.create(command("r3", 1)).isFailure)

        assertEquals(before, f.invoices.invoice)
        assertEquals(3, f.returns.getReturnedQuantity(line.id))
        assertEquals(listOf(60_00L, 60_00L), f.stock.sales.map { it.historicalUnitCostMinor })
        assertEquals(95_00L, f.stock.currentBuyPriceMinor)
        assertEquals(2, f.outbox.events.size)
    }

    @Test
    fun `same writeId is idempotent and does not repeat effects`() = runTest {
        val f = Fixture(invoice(DomainInvoiceCategory.SALE), listOf(saleLine(quantity = 2)))
        val first = f.coordinator.create(command("same", 1)).getOrThrow()
        val replay = f.coordinator.create(command("same", 1)).getOrThrow()

        assertFalse(first.duplicate)
        assertTrue(replay.duplicate)
        assertEquals(first.returnId, replay.returnId)
        assertEquals(1, f.stock.sales.size)
        assertEquals(1, f.outbox.events.size)
        assertEquals(1, f.credits.rows.size)
    }

    @Test
    fun `fully returning latest purchase marks source invalid so price can fall back`() = runTest {
        val f = Fixture(invoice(DomainInvoiceCategory.PURCHASE), listOf(purchaseLine(quantity = 2)))
        f.stock.currentBuyPriceMinor = 120_00L
        f.stock.previousValidBuyPriceMinor = 100_00L
        f.stock.latestOwnedByOriginal = true

        f.coordinator.create(command("purchase-return", 2)).getOrThrow()

        assertEquals(100_00L, f.stock.currentBuyPriceMinor)
        assertEquals(false, f.stock.purchases.single().sourceStillValidForItem)
    }

    @Test
    fun `newer purchase prevents silent price rollback`() = runTest {
        val f = Fixture(invoice(DomainInvoiceCategory.PURCHASE), listOf(purchaseLine(quantity = 2)))
        f.stock.currentBuyPriceMinor = 150_00L
        f.stock.previousValidBuyPriceMinor = 100_00L
        f.stock.latestOwnedByOriginal = false

        f.coordinator.create(command("old-purchase-return", 2)).getOrThrow()

        assertEquals(150_00L, f.stock.currentBuyPriceMinor)
    }

    @Test
    fun `cash refund is limited to effective paid amount and settles note atomically`() = runTest {
        val f = Fixture(invoice(DomainInvoiceCategory.SALE), listOf(saleLine(quantity = 2)))
        f.invoices.payments += payment(functionalMinor = 100_00L)

        val tooLarge = f.coordinator.create(command("refund-too-large", 2, InvoiceReturnSettlementMode.CASH_REFUND))
        assertTrue(tooLarge.isFailure)
        assertTrue(f.returns.events.isEmpty())
        assertTrue(f.cash.rows.isEmpty())

        val ok = f.coordinator.create(command("refund-ok", 1, InvoiceReturnSettlementMode.CASH_REFUND))
        assertTrue(ok.isSuccess)
        assertEquals(1, f.cash.rows.size)
        assertEquals(FakeCash.Direction.OUT, f.cash.rows.single().direction)
        assertEquals(100_00L, f.cash.rows.single().amountMinor)
        assertEquals(2, f.credits.rows.size) // credit note + opposite cash settlement
        assertEquals(0L, f.credits.rows.sumOf { it.amountMinor })
    }

    @Test
    fun `purchase cash refund records supplier cash inflow and consumes debit note balance`() = runTest {
        val f = Fixture(invoice(DomainInvoiceCategory.PURCHASE), listOf(purchaseLine(quantity = 2)))
        f.invoices.payments += payment(functionalMinor = 80_00L)

        val result = f.coordinator.create(command("purchase-cash", 1, InvoiceReturnSettlementMode.CASH_REFUND))

        assertTrue(result.isSuccess)
        assertEquals(FakeCash.Direction.IN, f.cash.rows.single().direction)
        assertEquals(80_00L, f.cash.rows.single().amountMinor)
        assertEquals(0L, f.credits.rows.sumOf { it.amountMinor })
    }

    @Test
    fun `return functional amount uses immutable invoice recognition ratio`() = runTest {
        val multiCurrency = invoice(DomainInvoiceCategory.SALE).copy(
            transactionCurrencyCode = "USD",
            functionalCurrencyCode = "SDG",
            transactionAmountMinor = 200_00L,
            functionalAmountAtRecognitionMinor = 5000_00L,
        )
        val f = Fixture(multiCurrency, listOf(saleLine(quantity = 2)))

        val result = f.coordinator.create(command("fx-return", 1)).getOrThrow()

        assertEquals(100_00L, result.transactionAmountMinor)
        assertEquals(2500_00L, result.functionalAmountMinor)
        assertEquals(2500_00L, f.returns.events.single().lines.single().functionalAmountMinor)
    }

    @Test
    fun `return fails closed when invoice currency truth is not known`() = runTest {
        val legacy = invoice(DomainInvoiceCategory.SALE).copy(legacyCurrencyStatus = LegacyCurrencyStatus.UNKNOWN)
        val f = Fixture(legacy, listOf(saleLine(quantity = 1)))

        assertTrue(f.coordinator.create(command("unknown-currency", 1)).isFailure)
        assertTrue(f.returns.events.isEmpty())
        assertTrue(f.stock.sales.isEmpty())
    }

    @Test
    fun `tracked sale return fails closed when historical COGS snapshot is unknown`() = runTest {
        val line = saleLine(quantity = 1).copy(costSnapshotStatus = "LEGACY_UNKNOWN")
        val f = Fixture(invoice(DomainInvoiceCategory.SALE), listOf(line))

        assertTrue(f.coordinator.create(command("unknown-cogs", 1)).isFailure)
        assertTrue(f.returns.events.isEmpty())
        assertTrue(f.stock.sales.isEmpty())
    }

    private fun command(
        writeId: String,
        quantity: Int,
        settlement: InvoiceReturnSettlementMode = InvoiceReturnSettlementMode.CREDIT_BALANCE,
    ) = CreateInvoiceReturnCommand(
        originalInvoiceId = "invoice",
        lines = listOf(InvoiceReturnLineRequest("line", quantity)),
        settlementMode = settlement,
        reason = "سبب اختبار صحيح",
        organizationId = "org",
        writeId = writeId,
        occurredAt = 1_700_000_000_000L,
    )

    private fun invoice(category: DomainInvoiceCategory) = InvoiceRecord(
        id = "invoice",
        invoiceNumber = 1,
        clientId = "party",
        organizationId = "org",
        category = category,
        description = "original",
        totalAmount = if (category == DomainInvoiceCategory.SALE) 200.0 else 160.0,
        totalAmountMinor = if (category == DomainInvoiceCategory.SALE) 200_00 else 160_00,
        transactionCurrencyCode = "SDG",
        functionalCurrencyCode = "SDG",
        transactionAmountMinor = if (category == DomainInvoiceCategory.SALE) 200_00 else 160_00,
        functionalAmountAtRecognitionMinor = if (category == DomainInvoiceCategory.SALE) 200_00 else 160_00,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        dueDate = 0L,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED,
        lifecycleVersion = 1,
    )

    private fun saleLine(quantity: Int) = InvoiceLine(
        id = "line",
        invoiceId = "invoice",
        itemName = "Part",
        quantity = quantity,
        sellPrice = 100.0,
        sellPriceMinor = 100_00,
        unitSellPrice = 100.0,
        unitSellPriceMinor = 100_00,
        totalPrice = quantity * 100.0,
        totalPriceMinor = quantity * 100_00L,
        unitCostAtSale = 60.0,
        unitCostAtSaleMinor = 60_00,
        costSnapshotStatus = "KNOWN",
        inventoryItemId = "item",
    )

    private fun purchaseLine(quantity: Int) = InvoiceLine(
        id = "line",
        invoiceId = "invoice",
        itemName = "Part",
        quantity = quantity,
        buyPrice = 80.0,
        buyPriceMinor = 80_00,
        totalPrice = quantity * 80.0,
        totalPriceMinor = quantity * 80_00L,
        inventoryItemId = "item",
    )

    private fun payment(functionalMinor: Long) = InvoicePayment(
        id = "payment",
        invoiceId = "invoice",
        clientId = "party",
        amount = functionalMinor / 100.0,
        amountMinor = functionalMinor,
        supplierAmountMinor = functionalMinor,
        functionalCashAmountMinor = functionalMinor,
        historicalFunctionalAmountMinor = functionalMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        paidAt = 1_600_000_000_000L,
    )

    private class Fixture(invoice: InvoiceRecord, lines: List<InvoiceLine>) {
        val invoices = FakeInvoiceStore(invoice, lines)
        val returns = FakeReturnStore()
        val stock = FakeReturnStock()
        val credits = FakeCredits()
        val cash = FakeCash()
        val outbox = FakeOutbox()
        private val tx = object : InvoiceTransactionPort {
            override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
        }
        val coordinator = InvoiceReturnCoordinator(
            transaction = tx,
            invoices = invoices,
            returns = returns,
            stock = stock,
            credits = credits,
            cash = cash,
            authorization = object : InvoiceReturnAuthorizationPort {
                override suspend fun canCreate(category: DomainInvoiceCategory) = true
            },
            outbox = outbox,
            audit = object : WriteAuditPort { override suspend fun write(record: AuditRecord) = Unit },
            sessionReader = FakeSession(),
        )
    }

    private class FakeReturnStore : InvoiceReturnStorePort {
        val events = mutableListOf<InvoiceReturnAggregate>()
        override suspend fun getByWriteId(organizationId: String, writeId: String) =
            events.firstOrNull { it.document.organizationId == organizationId && it.document.writeId == writeId }?.document
        override suspend fun getReturnedQuantity(originalInvoiceItemId: String) = events.sumOf { event ->
            event.lines.filter { it.originalInvoiceItemId == originalInvoiceItemId }.sumOf { it.quantity }
        }
        override suspend fun getReturnedQuantityForInvoiceInventoryItem(invoiceId: String, inventoryItemId: String) = events
            .filter { it.document.originalInvoiceId == invoiceId }
            .sumOf { event -> event.lines.filter { it.inventoryItemId == inventoryItemId }.sumOf { it.quantity } }
        override suspend fun getAllocatedFunctionalForPayment(paymentId: String) = events.sumOf { event ->
            event.paymentAllocations.filter { it.paymentId == paymentId }.sumOf { it.allocatedFunctionalAmountMinor }
        }
        override suspend fun insertAggregate(aggregate: InvoiceReturnAggregate) { events += aggregate }
    }

    private class FakeReturnStock : InvoiceReturnStockPort {
        data class Sale(val historicalUnitCostMinor: Long)
        data class Purchase(val sourceStillValidForItem: Boolean)
        val sales = mutableListOf<Sale>()
        val purchases = mutableListOf<Purchase>()
        var currentBuyPriceMinor = 95_00L
        var previousValidBuyPriceMinor = 95_00L
        var latestOwnedByOriginal = true
        override suspend fun restoreSalesReturn(itemId: String, quantity: Int, returnId: String, returnLineId: String, clientId: String, historicalUnitCostMinor: Long, occurredAt: Long, writeId: String) {
            sales += Sale(historicalUnitCostMinor)
        }
        override suspend fun deductPurchaseReturn(itemId: String, quantity: Int, returnId: String, returnLineId: String, supplierId: String, originalInvoiceId: String, originalInvoiceItemId: String, internationalPurchase: Boolean, originalUnitCostMinor: Long, sourceStillValidForItem: Boolean, occurredAt: Long, writeId: String, actorId: String, actorName: String) {
            purchases += Purchase(sourceStillValidForItem)
            if (!sourceStillValidForItem && latestOwnedByOriginal) currentBuyPriceMinor = previousValidBuyPriceMinor
        }
    }

    private class FakeCredits : InvoiceReturnCreditPort {
        data class Row(val amountMinor: Long)
        val rows = mutableListOf<Row>()
        override suspend fun record(id: String, clientId: String, signedFunctionalAmountMinor: Long, note: String, sourceReference: String, occurredAt: Long, actorId: String, actorName: String) {
            rows += Row(signedFunctionalAmountMinor)
        }
    }

    private class FakeCash : InvoiceReturnCashPort {
        enum class Direction { OUT, IN }
        data class Row(val direction: Direction, val amountMinor: Long)
        val rows = mutableListOf<Row>()
        override suspend fun cashOut(amountMinor: Long, returnId: String, writeId: String, note: String) {
            rows += Row(Direction.OUT, amountMinor)
        }
        override suspend fun cashIn(amountMinor: Long, returnId: String, writeId: String, note: String) {
            rows += Row(Direction.IN, amountMinor)
        }
    }

    private class FakeOutbox : InvoiceReturnOutboxPort {
        val events = mutableListOf<InvoiceReturnAggregate>()
        override suspend fun append(aggregate: InvoiceReturnAggregate) { events += aggregate }
    }

    private class FakeInvoiceStore(
        var invoice: InvoiceRecord,
        private val lines: List<InvoiceLine>,
    ) : InvoiceStorePort {
        val payments = mutableListOf<InvoicePayment>()
        override suspend fun getInvoiceById(invoiceId: String) = invoice.takeIf { it.id == invoiceId }
        override suspend fun getInvoiceItems(invoiceId: String) = lines
        override suspend fun getTotalPaid(invoiceId: String) = payments.sumOf { it.amount }
        override suspend fun getPayments(invoiceId: String) = payments.toList()
        override suspend fun getPaymentAllocations(invoiceId: String) = emptyList<InvoicePaymentAllocation>()
        override suspend fun getRealizedFxEvents(invoiceId: String) = emptyList<RealizedFxEvent>()
        override suspend fun getReversalForPayment(originalPaymentId: String) = null
        override suspend fun claimWrite(identity: InvoiceWriteIdentity) = error("not used")
        override suspend fun insertInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) = error("not used")
        override suspend fun updateInvoice(invoice: InvoiceRecord) = error("original invoice must not be mutated")
        override suspend fun updatePostedDescription(invoice: InvoiceRecord, expectedVersion: Int) = error("not used")
        override suspend fun updateInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) = error("original invoice must not be mutated")
        override suspend fun addPayment(payment: InvoicePayment) = error("not used")
        override suspend fun addPaymentAllocation(allocation: InvoicePaymentAllocation) = error("not used")
        override suspend fun addRealizedFxEvent(event: RealizedFxEvent) = error("not used")
        override suspend fun deletePayments(invoiceId: String) = error("not used")
        override suspend fun markVoided(invoiceId: String, expectedVersion: Int, voidedAt: Long, reason: String, writeId: String) = error("not used")
    }

    private class FakeSession : SessionReader {
        private val u = CurrentUser("user", "Tester", "")
        private val o = CurrentOrganization("org")
        override val userId = flowOf(u.id)
        override val userName = flowOf(u.name)
        override val userPhone = flowOf(u.phone)
        override val role = flowOf("admin")
        override val permissionsJson = flowOf("")
        override val organizationId = flowOf(o.id)
        override val currentUser = flowOf(u)
        override val currentOrganization = flowOf(o)
        override suspend fun snapshot() = SessionState(u, o, "admin", "")
    }
}
