package com.verto.app.feature.payment.application

import com.verto.app.core.audit.domain.*
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.*
import com.verto.app.feature.payment.domain.model.*
import com.verto.app.feature.payment.domain.port.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PaymentCoordinatorsTest {
    @Test fun `record rejects overpayment before persistence`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0)
        val result = f.record.record(command(amount = 100.01, remaining = 100.0))
        assertTrue(result is PaymentOperationResult.Error)
        assertTrue(f.store.inserted.isEmpty())
    }

    @Test fun `database minor total wins over stale presentation remaining`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0)
        f.store.paid = 0.0 // deliberately stale legacy projection
        f.store.paidMinor = 9_000L

        val result = f.record.record(command(amount = 20.0, remaining = 100.0))

        assertTrue(result is PaymentOperationResult.Error)
        assertTrue(f.store.inserted.isEmpty())
        assertEquals(0.0, f.cash.received, 0.0)
    }

    @Test fun `sale payment is persisted and enters cash`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0, category = PaymentInvoiceCategory.SALE)
        val result = f.record.record(command(amount = 25.0, remaining = 100.0))
        assertEquals(PaymentOperationResult.Success("req"), result)
        assertEquals(25.0, f.cash.received, 0.0)
        assertEquals("employee", f.store.inserted.single().employeeId)
        assertEquals(1, f.audit.records.size)
        assertEquals(1, f.atomic.calls)
    }

    @Test fun `purchase payment leaves cash`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0, category = PaymentInvoiceCategory.PURCHASE)
        f.record.record(command(amount = 20.0, remaining = 100.0))
        assertEquals(20.0, f.cash.made, 0.0)
    }

    @Test fun `retry with same request id produces one local financial effect`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0)

        val first = f.record.record(command(amount = 10.0, remaining = 100.0))
        val retry = f.record.record(command(amount = 10.0, remaining = 100.0))

        assertEquals(PaymentOperationResult.Success("req"), first)
        assertTrue(retry is PaymentOperationResult.Error)
        assertEquals(1, f.store.inserted.size)
        assertEquals(10.0, f.cash.received, 0.0)
        assertEquals(1, f.atomic.calls)
    }

    @Test fun `legacy remote mutation flag cannot bypass local first outbox path`() = runTest {
        val f = Fixture()
        f.remote.enabled = true
        f.store.invoice = invoice(total = 100.0)

        val result = f.record.record(command(amount = 10.0, remaining = 100.0))

        assertEquals(PaymentOperationResult.Success("req"), result)
        assertEquals(1, f.store.inserted.size)
        assertEquals(1, f.atomic.calls)
        assertEquals(0, f.remote.postCalls)
    }

    @Test fun `reverse rejects already reversed movement`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0)
        f.store.payment = PaymentRecord("p", "inv", "client", 10.0, paymentMethod = PaymentMethod.CASH)
        f.store.reversal = PaymentRecord("r", "inv", "client", -10.0, paymentMethod = PaymentMethod.CASH, reversedPaymentId = "p")
        val r = f.reverse.reverse(ReversePaymentCommand("p", "rev"))
        assertTrue(r is PaymentOperationResult.Error)
    }

    @Test fun `reverse sale creates negative record and reverses cash`() = runTest {
        val f = Fixture()
        f.store.invoice = invoice(total = 100.0)
        f.store.payment = PaymentRecord("p", "inv", "client", 10.0, paymentMethod = PaymentMethod.CASH)
        val r = f.reverse.reverse(ReversePaymentCommand("p", "rev"))
        assertEquals(PaymentOperationResult.Success("rev"), r)
        assertEquals(-10.0, f.store.inserted.single().amount, 0.0)
        assertEquals(10.0, f.cash.reversedReceived, 0.0)
        assertEquals(1, f.atomic.calls)
    }

    @Test fun `legacy remote reverse is never called`() = runTest {
        val f = Fixture()
        f.remote.enabled = true
        f.store.invoice = invoice(total = 100.0)
        f.store.payment = PaymentRecord("p", "inv", "client", 10.0, paymentMethod = PaymentMethod.CASH)

        val result = f.reverse.reverse(ReversePaymentCommand("p", "rev"))

        assertEquals(PaymentOperationResult.Success("rev"), result)
        assertEquals(0, f.remote.reverseCalls)
        assertEquals(1, f.atomic.calls)
    }

    private fun invoice(
        total: Double,
        category: PaymentInvoiceCategory = PaymentInvoiceCategory.SALE,
    ) = PaymentInvoice("inv", 7, "client", category, total)

    private fun command(amount: Double, remaining: Double?) = RecordPaymentCommand(
        "inv", "client", amount, PaymentMethod.CASH,
        remainingAmount = remaining, clientName = "Client", invoiceNumber = 7,
        paidAt = 10, requestId = "req",
    )

    private class Fixture {
        val store = FakeStore()
        val cash = FakeCash()
        val audit = FakeAudit()
        val session = FakeSession()
        val auth = FakeAuth()
        val remote = FakeRemote()
        val atomic = FakeAtomic()
        val tx = object : PaymentTransactionPort {
            override suspend fun <T> inTransaction(block: suspend () -> T) = block()
        }
        val record = RecordPaymentCoordinator(tx, store, cash, audit, session, auth, remote, atomic)
        val reverse = ReversePaymentCoordinator(tx, store, cash, audit, session, auth, remote, atomic)
    }

    private class FakeStore : PaymentStorePort {
        var invoice: PaymentInvoice? = null
        var payment: PaymentRecord? = null
        var reversal: PaymentRecord? = null
        var paid = 0.0
        var paidMinor = 0L
        var acceptInsert = true
        val inserted = mutableListOf<PaymentRecord>()

        override suspend fun getInvoice(invoiceId: String) = invoice
        override suspend fun getTotalPaid(invoiceId: String) = paid
        override suspend fun getTotalPaidMinor(invoiceId: String) = paidMinor
        override suspend fun insertPayment(payment: PaymentRecord): Boolean {
            if (!acceptInsert || inserted.any { it.id == payment.id }) return false
            inserted += payment
            paid += payment.amount
            paidMinor = Math.addExact(paidMinor, payment.amountMinor)
            return true
        }
        override suspend fun insertAllocation(allocation: PaymentAllocationRecord) = Unit
        override suspend fun insertRealizedFxEvent(event: RealizedFxEventRecord) = Unit
        override suspend fun getPayment(paymentId: String) = payment
        override suspend fun getReversalFor(paymentId: String) = reversal
        override suspend fun mirrorPayment(payment: PaymentRecord) { inserted += payment }
    }

    private class FakeCash : PaymentCashPort {
        var received = 0.0
        var made = 0.0
        var reversedReceived = 0.0
        var reversedMade = 0.0
        override suspend fun onPaymentReceived(amount: Double, paymentId: String) { received += amount }
        override suspend fun onPaymentMade(amount: Double, paymentId: String) { made += amount }
        override suspend fun reversePaymentReceived(amount: Double, paymentId: String, note: String) { reversedReceived += amount }
        override suspend fun reversePaymentMade(amount: Double, paymentId: String, note: String) { reversedMade += amount }
    }

    private class FakeAuth : PaymentAuthorizationPort {
        override suspend fun canRecordClientPayment() = true
        override suspend fun canRecordSupplierPayment() = true
        override suspend fun canReversePayment() = true
    }

    private class FakeRemote : PaymentRemotePort {
        var enabled = false
        var postCalls = 0
        var reverseCalls = 0
        override fun isEnabled() = enabled
        override suspend fun postPayment(payment: PaymentRecord, cashAmount: Double): String {
            postCalls += 1
            return "remote"
        }
        override suspend fun reversePayment(paymentId: String, requestId: String): String {
            reverseCalls += 1
            return "remote-rev"
        }
    }

    private class FakeAtomic : PaymentAtomicPersistenceCoordinator {
        var calls = 0
        override suspend fun persist(command: PersistPaymentIntegrationCommand): Result<Unit> {
            calls += 1
            return Result.success(Unit)
        }
    }

    private class FakeAudit : WriteAuditPort {
        val records = mutableListOf<AuditRecord>()
        override suspend fun write(record: AuditRecord) { records += record }
    }

    private class FakeSession : SessionReader {
        private val u = CurrentUser("employee", "Emp", "249")
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
