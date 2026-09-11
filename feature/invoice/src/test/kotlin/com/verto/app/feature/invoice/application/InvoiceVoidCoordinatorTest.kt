package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.*
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.*
import com.verto.app.feature.invoice.domain.model.*
import com.verto.app.feature.invoice.domain.model.InvoiceCategory as DomainInvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceStatus as DomainInvoiceStatus
import com.verto.app.feature.invoice.domain.port.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InvoiceVoidCoordinatorTest {
    @Test fun `void reverses payment allocation cash and lifecycle once`() = runTest {
        val f = Fixture(invoice())
        f.store.payments += payment(100_00, functionalMinor = 100_00)
        f.store.allocations += InvoicePaymentAllocation(
            id = "a1", paymentId = "p10000", invoiceId = "i",
            allocatedTransactionAmountMinor = 100_00,
            historicalFunctionalAmountMinor = 100_00,
            realizedFxDifferenceMinor = 0,
        )
        val request = InvoiceVoidRequest("i", "خطأ في الفاتورة", InvoiceVoidPaymentDisposition.REFUND_TO_CASH, "void-i")

        f.coordinator.void(request)
        f.coordinator.void(request)

        assertEquals(listOf("i"), f.stock.reversed)
        assertEquals(1, f.cash.movements.size)
        assertEquals(InvoiceCashMovementType.SALE_CASH, f.cash.movements.single().first)
        assertEquals(100.0, f.cash.movements.single().second, 0.0)
        assertEquals(1, f.store.payments.count { it.reversedPaymentId == "p10000" })
        assertEquals(-100_00L, f.store.allocations.single { it.paymentId != "p10000" }.allocatedTransactionAmountMinor)
        assertEquals(InvoiceLifecycleStatus.VOID, f.store.invoice?.lifecycleStatus)
        assertEquals("خطأ في الفاتورة", f.store.invoice?.voidReason)
    }

    @Test fun `paid invoice requires explicit payment disposition`() = runTest {
        val f = Fixture(invoice())
        f.store.payments += payment(100_00, functionalMinor = 100_00)
        val error = runCatching { f.coordinator.void(InvoiceVoidRequest("i", "سبب صحيح", requestId = "r")) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(InvoiceLifecycleStatus.POSTED, f.store.invoice?.lifecycleStatus)
        assertTrue(f.stock.reversed.isEmpty())
    }

    @Test fun `international purchase refunds historical functional cash not invoice amount`() = runTest {
        val f = Fixture(invoice(category = DomainInvoiceCategory.PURCHASE, status = DomainInvoiceStatus.CLOSED_CASH, scope = PurchaseScope.INTERNATIONAL))
        f.store.payments += payment(100_00, functionalMinor = 256_000_00)
        f.coordinator.void(InvoiceVoidRequest("i", "إلغاء شراء", InvoiceVoidPaymentDisposition.REFUND_TO_CASH, "r"))
        assertEquals(InvoiceCashMovementType.PURCHASE_CASH, f.cash.movements.single().first)
        assertEquals(256000.0, f.cash.movements.single().second, 0.0)
    }

    @Test fun `shipment linked invoice is blocked before mutations`() = runTest {
        val f = Fixture(invoice().copy(shipmentId = "shipment"))
        val error = runCatching { f.coordinator.void(InvoiceVoidRequest("i", "سبب صحيح", requestId = "r")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(f.stock.reversed.isEmpty())
    }

    @Test(expected = InvoiceAuthorizationException::class)
    fun `permission denial blocks all mutations`() = runTest {
        val f = Fixture(invoice())
        f.auth.allowed = false
        f.coordinator.void(InvoiceVoidRequest("i", "سبب صحيح", requestId = "r"))
    }

    private fun invoice(
        status: DomainInvoiceStatus = DomainInvoiceStatus.CLOSED_CASH,
        category: DomainInvoiceCategory = DomainInvoiceCategory.SALE,
        scope: PurchaseScope = PurchaseScope.LOCAL,
    ) = InvoiceRecord(
        id = "i", invoiceNumber = 7, clientId = "c", organizationId = "org",
        category = category, description = "Invoice", totalAmount = 100.0, totalAmountMinor = 100_00,
        transactionCurrencyCode = if (scope == PurchaseScope.INTERNATIONAL) "USD" else "SDG",
        functionalCurrencyCode = "SDG", dueDate = 0L, status = status, purchaseScope = scope,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED, lifecycleVersion = 1,
    )

    private fun payment(amountMinor: Long, functionalMinor: Long) = InvoicePayment(
        id = "p$amountMinor", invoiceId = "i", clientId = "c",
        amount = amountMinor / 100.0, amountMinor = amountMinor, supplierAmountMinor = amountMinor,
        functionalCashAmountMinor = functionalMinor, historicalFunctionalAmountMinor = functionalMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
    )

    private class Fixture(initial: InvoiceRecord?) {
        val store = FakeStore(initial)
        val stock = FakeStock()
        val cash = FakeCash()
        val audit = FakeAudit()
        val auth = FakeAuth()
        val tx = object : InvoiceTransactionPort { override suspend fun <T> inTransaction(block: suspend () -> T): T = block() }
        val coordinator = InvoiceVoidCoordinator(tx, store, stock, cash, audit, FakeSession(), auth)
    }

    private class FakeStore(var invoice: InvoiceRecord?) : InvoiceStorePort {
        var payments = mutableListOf<InvoicePayment>()
        var allocations = mutableListOf<InvoicePaymentAllocation>()
        var fx = mutableListOf<RealizedFxEvent>()
        val claims = mutableSetOf<String>()
        override suspend fun getInvoiceById(invoiceId: String) = invoice
        override suspend fun getInvoiceItems(invoiceId: String) = emptyList<InvoiceLine>()
        override suspend fun getTotalPaid(invoiceId: String) = payments.sumOf { it.amount }
        override suspend fun getPayments(invoiceId: String) = payments.toList()
        override suspend fun getPaymentAllocations(invoiceId: String) = allocations.toList()
        override suspend fun getRealizedFxEvents(invoiceId: String) = fx.toList()
        override suspend fun getReversalForPayment(originalPaymentId: String) = payments.firstOrNull { it.reversedPaymentId == originalPaymentId }
        override suspend fun claimWrite(identity: InvoiceWriteIdentity) = InvoiceWriteClaim(identity.invoiceId, claims.add("${identity.operation}:${identity.writeId}"))
        override suspend fun insertInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) = invoice.id to invoice.invoiceNumber
        override suspend fun updateInvoice(invoice: InvoiceRecord) { this.invoice = invoice }
        override suspend fun updatePostedDescription(invoice: InvoiceRecord, expectedVersion: Int) = true
        override suspend fun updateInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) { this.invoice = invoice }
        override suspend fun addPayment(payment: InvoicePayment): String { payments += payment; return payment.id }
        override suspend fun addPaymentAllocation(allocation: InvoicePaymentAllocation) { allocations += allocation }
        override suspend fun addRealizedFxEvent(event: RealizedFxEvent) { fx += event }
        override suspend fun deletePayments(invoiceId: String) = Unit
        override suspend fun markVoided(invoiceId: String, expectedVersion: Int, voidedAt: Long, reason: String, writeId: String): Boolean {
            val current = invoice ?: return false
            if (current.lifecycleVersion != expectedVersion || current.lifecycleStatus != InvoiceLifecycleStatus.POSTED) return false
            invoice = current.copy(lifecycleStatus = InvoiceLifecycleStatus.VOID, lifecycleVersion = current.lifecycleVersion + 1, voidedAt = voidedAt, voidReason = reason, voidWriteId = writeId, voided = true)
            return true
        }
    }

    private class FakeStock : InvoiceStockPort {
        val reversed = mutableListOf<String>()
        override suspend fun getAllItems() = emptyList<InvoiceStockItem>()
        override suspend fun getItem(itemId: String): InvoiceStockItem? = null
        override suspend fun saveItem(item: InvoiceStockItem) = Unit
        override suspend fun deductStock(itemId: String, quantity: Int, invoiceId: String, clientId: String, unitPrice: Double, allowNegativeStock: Boolean, sourceWriteId: String) = Result.success(Unit)
        override suspend fun addStock(itemId: String, quantity: Int, invoiceId: String, supplierId: String, unitPrice: Double, sourceWriteId: String) = Result.success(Unit)
        override suspend fun receivePurchaseAtLatestPrice(command: InvoicePurchaseStockCommand): InvoiceInventoryRevaluationRecord? = null
        override suspend fun deleteMovements(invoiceId: String) = Unit
        override suspend fun reverseMovements(invoiceId: String, sourceWriteId: String) { reversed += invoiceId }
    }

    private class FakeCash : InvoiceCashPort {
        val movements = mutableListOf<Pair<InvoiceCashMovementType, Double>>()
        override suspend fun onSaleCash(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun onPurchaseCash(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun onPaymentReceived(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun recordMovement(type: InvoiceCashMovementType, amount: Double, referenceId: String, note: String, sourceWriteId: String) = Unit
        override suspend fun reverseMovement(type: InvoiceCashMovementType, amount: Double, referenceId: String, note: String, sourceWriteId: String) { movements += type to amount }
    }

    private class FakeAuth : InvoiceAuthorizationPort {
        var allowed = true
        override suspend fun canSave(isSale: Boolean, creatingNew: Boolean) = true
        override suspend fun canPost(category: DomainInvoiceCategory) = true
        override suspend fun canEditDescription(category: DomainInvoiceCategory) = true
        override suspend fun canOverrideStock() = true
        override suspend fun canApproveExchangeRate() = true
        override suspend fun canManuallyAllocateLandedCost() = true
        override suspend fun canVoid(category: DomainInvoiceCategory) = allowed
    }

    private class FakeAudit : WriteAuditPort {
        val records = mutableListOf<AuditRecord>()
        override suspend fun write(record: AuditRecord) { records += record }
    }

    private class FakeSession : SessionReader {
        private val u = CurrentUser("u", "U", "")
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
