package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.AuditRecord
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceDraftItem
import com.verto.app.feature.invoice.domain.model.InvoiceInventoryRevaluationRecord
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.InvoicePaymentAllocation
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.model.InvoiceSaleStockMutation
import com.verto.app.feature.invoice.domain.model.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.InvoiceWriteClaim
import com.verto.app.feature.invoice.domain.model.InvoiceWriteIdentity
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand
import com.verto.app.feature.invoice.domain.model.RealizedFxEvent
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.model.InvoicePostingIdentity
import com.verto.app.feature.invoice.domain.port.InvoiceAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceNumberPort
import com.verto.app.feature.invoice.domain.port.InvoiceSettingsPort
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceSyncSchedulerPort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.feature.invoice.domain.port.InvoiceLinePostingStockPort
import com.verto.app.feature.invoice.domain.model.InvoiceCashMovementType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceWriteCoordinatorTest {
    @Test
    fun `new invoice uses injected ids and clock only`() = runTest {
        val fixture = Fixture(ids = listOf("write-1", "invoice-1", "line-1"), now = 123456L)

        val result = fixture.coordinator.save(fixture.command(writeId = "", requestedAt = 0L, originalCreatedAt = null))

        assertEquals("invoice-1", result.invoiceId)
        assertEquals("invoice-1", fixture.store.insertedInvoice?.id)
        assertEquals(123456L, fixture.store.insertedInvoice?.createdAt)
        assertEquals("line-1", fixture.store.insertedLines.single().id)
        assertEquals("write-1", fixture.atomic.commands.single().writeId)
        assertEquals(123456L, fixture.atomic.commands.single().occurredAt)
        assertEquals(2, fixture.identity.nowCalls)
        assertEquals(3, fixture.identity.idCalls)
    }

    @Test
    fun `explicit write identity and requested time remain authoritative`() = runTest {
        val fixture = Fixture(ids = listOf("invoice-2", "line-2"), now = 999999L)

        fixture.coordinator.save(
            fixture.command(
                writeId = "stable-write",
                requestedAt = 777L,
                originalCreatedAt = 555L,
            ),
        )

        val integration = fixture.atomic.commands.single()
        assertEquals("stable-write", integration.writeId)
        assertEquals(777L, integration.occurredAt)
        assertEquals(555L, fixture.store.insertedInvoice?.createdAt)
        assertEquals(0, fixture.identity.nowCalls)
        assertEquals(2, fixture.identity.idCalls)
    }

    @Test
    fun `create path preserves persistence then post commit ordering`() = runTest {
        val fixture = Fixture(ids = listOf("invoice-3", "line-3"))

        fixture.coordinator.save(fixture.command(writeId = "write-3", requestedAt = 100L, originalCreatedAt = 90L))

        assertOrdered(
            fixture.events,
            "tx-begin",
            "claim:CREATE",
            "insert",
            "integration:CREATED",
            "audit:INVOICE",
            "tx-end",
            "sync",
        )
    }

    @Test
    fun `edit routes existing draft through update without replacing identity`() = runTest {
        val existing = existingDraft("existing-1")
        val fixture = Fixture(ids = listOf("edit-line"), existing = existing)
        fixture.store.linesByInvoice[existing.id] = mutableListOf(existingLine(existing.id))

        val result = fixture.coordinator.save(
            fixture.command(
                existingInvoiceId = existing.id,
                writeId = "edit-write",
                requestedAt = 800L,
                originalCreatedAt = existing.createdAt,
                originalInvoiceNumber = existing.invoiceNumber,
                notes = "updated",
            ),
        )

        assertEquals(existing.id, result.invoiceId)
        assertEquals(0, fixture.store.insertCount)
        assertEquals(1, fixture.store.fullUpdateCount)
        assertEquals(existing.id, fixture.store.lastFullUpdate?.id)
        assertEquals("UPDATED", fixture.atomic.commands.single().writeKind.name)
    }

    @Test
    fun `authorization rejection blocks persistence and post commit effects`() = runTest {
        val fixture = Fixture(ids = listOf("write-denied", "invoice-denied", "line-denied"))
        fixture.auth.allowPost = false

        val failure = runCatching { fixture.coordinator.save(fixture.command()) }.exceptionOrNull()

        assertTrue(failure is SecurityException)
        assertEquals(0, fixture.store.claimCount)
        assertEquals(0, fixture.store.insertCount)
        assertEquals(0, fixture.scheduler.calls)
        assertTrue(fixture.atomic.commands.isEmpty())
    }

    @Test
    fun `validation failure blocks persistence side effects`() = runTest {
        val fixture = Fixture(ids = listOf("write-invalid", "invoice-invalid"))

        val failure = runCatching { fixture.coordinator.save(fixture.command(items = emptyList())) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, fixture.store.claimCount)
        assertEquals(0, fixture.store.insertCount)
        assertEquals(0, fixture.scheduler.calls)
        assertTrue(fixture.atomic.commands.isEmpty())
    }

    @Test
    fun `post commit effects never run when persistence fails`() = runTest {
        val fixture = Fixture(ids = listOf("invoice-fail", "line-fail"))
        fixture.store.failInsert = true

        val failure = runCatching {
            fixture.coordinator.save(fixture.command(writeId = "write-fail", requestedAt = 10L, originalCreatedAt = 10L))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, fixture.store.claimCount)
        assertEquals(0, fixture.scheduler.calls)
        assertTrue(fixture.atomic.commands.isEmpty())
    }

    @Test
    fun `duplicate create returns previously claimed invoice without repeating effects`() = runTest {
        val duplicate = existingPosted("duplicate-existing")
        val fixture = Fixture(ids = listOf("new-attempt-id", "new-line"), existing = duplicate)
        fixture.store.claimOverride = InvoiceWriteClaim(duplicate.id, claimed = false)

        val result = fixture.coordinator.save(
            fixture.command(writeId = "same-write", requestedAt = 20L, originalCreatedAt = 20L),
        )

        assertEquals(duplicate.id, result.invoiceId)
        assertEquals(0, fixture.store.insertCount)
        assertEquals(0, fixture.scheduler.calls)
        assertTrue(fixture.atomic.commands.isEmpty())
        assertFalse(fixture.events.contains("audit:INVOICE"))
    }

    private class Fixture(
        ids: List<String> = listOf("write", "invoice", "line"),
        now: Long = 4242L,
        existing: InvoiceRecord? = null,
    ) {
        val events = mutableListOf<String>()
        val identity = FakeIdentityFactory(ids, now)
        val store = FakeStore(events, existing)
        val stock = FakeStock()
        val cash = FakeCash()
        val auth = FakeAuthorization()
        val scheduler = FakeSyncScheduler(events)
        val atomic = FakeAtomic(events)
        private val audit = FakeAudit(events)
        private val transaction = object : InvoiceTransactionPort {
            override suspend fun <T> inTransaction(block: suspend () -> T): T {
                events += "tx-begin"
                return try {
                    block()
                } finally {
                    events += "tx-end"
                }
            }
        }
        private val settings = object : InvoiceSettingsPort {
            override suspend fun allowNegativeStock() = false
            override suspend fun functionalCurrencyCode() = "SDG"
        }
        private val number = object : InvoiceNumberPort {
            override suspend fun allocate(): Int = 77
        }

        val coordinator = InvoiceWriteCoordinator(
            transactionPort = transaction,
            store = store,
            sessionReader = FakeSession(),
            settings = settings,
            authorization = auth,
            preparation = InvoiceWritePreparation(number, InvoiceSaveValidator(), InvoiceDraftFactory(), identity),
            editPolicy = InvoiceEditPolicy(),
            inventoryWriter = InvoiceInventoryWriter(stock),
            paymentWriter = InvoicePaymentWriter(store, cash),
            auditLogger = audit,
            postCommitEffects = InvoicePostCommitEffects(scheduler),
            atomicPersistenceCoordinator = atomic,
            purchaseCycle = null,
        )

        fun command(
            existingInvoiceId: String? = null,
            items: List<InvoiceDraftItem> = listOf(
                InvoiceDraftItem(name = "Widget", quantity = "1", sellPrice = "10", buyPrice = ""),
            ),
            writeId: String = "",
            requestedAt: Long = 0L,
            originalCreatedAt: Long? = null,
            originalInvoiceNumber: Int? = null,
            notes: String = "note",
        ) = SaveInvoiceCommand(
            existingInvoiceId = existingInvoiceId,
            clientId = "client",
            items = items,
            paymentMode = InvoicePaymentMode.CREDIT,
            dueDate = 10_000L,
            notes = notes,
            isSale = true,
            originalCreatedAt = originalCreatedAt,
            originalInvoiceNumber = originalInvoiceNumber,
            organizationId = "org",
            functionalCurrencyCode = "SDG",
            transactionCurrencyCode = "SDG",
            writeId = writeId,
            requestedAt = requestedAt,
        )
    }

    private class FakeIdentityFactory(ids: List<String>, private val now: Long) : InvoiceWriteIdentityFactory() {
        private val remaining = java.util.ArrayDeque(ids)
        var idCalls = 0
        var nowCalls = 0
        override fun newId(): String {
            idCalls += 1
            return if (remaining.isEmpty()) error("Unexpected generated id #$idCalls") else remaining.removeFirst()
        }
        override fun nowMillis(): Long {
            nowCalls += 1
            return now
        }
    }

    private class FakeStore(
        private val events: MutableList<String>,
        existing: InvoiceRecord?,
    ) : InvoiceStorePort {
        val invoices = mutableMapOf<String, InvoiceRecord>()
        val linesByInvoice = mutableMapOf<String, MutableList<InvoiceLine>>()
        val payments = mutableListOf<InvoicePayment>()
        val allocations = mutableListOf<InvoicePaymentAllocation>()
        val fx = mutableListOf<RealizedFxEvent>()
        var insertedInvoice: InvoiceRecord? = null
        var insertedLines: List<InvoiceLine> = emptyList()
        var insertCount = 0
        var fullUpdateCount = 0
        var lastFullUpdate: InvoiceRecord? = null
        var claimCount = 0
        var claimOverride: InvoiceWriteClaim? = null
        var failInsert = false

        init {
            if (existing != null) invoices[existing.id] = existing
        }

        override suspend fun getInvoiceById(invoiceId: String) = invoices[invoiceId]
        override suspend fun getInvoiceItems(invoiceId: String) = linesByInvoice[invoiceId].orEmpty()
        override suspend fun getTotalPaid(invoiceId: String) = payments.filter { it.invoiceId == invoiceId }.sumOf { it.amount }
        override suspend fun getPayments(invoiceId: String) = payments.filter { it.invoiceId == invoiceId }
        override suspend fun getPaymentAllocations(invoiceId: String) = allocations.filter { it.invoiceId == invoiceId }
        override suspend fun getRealizedFxEvents(invoiceId: String) = fx.filter { it.invoiceId == invoiceId }
        override suspend fun getReversalForPayment(originalPaymentId: String) = payments.firstOrNull { it.reversedPaymentId == originalPaymentId }
        override suspend fun claimWrite(identity: InvoiceWriteIdentity): InvoiceWriteClaim {
            claimCount += 1
            events += "claim:${identity.operation.name}"
            return claimOverride ?: InvoiceWriteClaim(identity.invoiceId, claimed = true)
        }
        override suspend fun insertInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>): Pair<String, Int> {
            if (failInsert) error("insert failed")
            insertCount += 1
            insertedInvoice = invoice
            insertedLines = items
            invoices[invoice.id] = invoice
            linesByInvoice[invoice.id] = items.toMutableList()
            events += "insert"
            return invoice.id to invoice.invoiceNumber
        }
        override suspend fun updateInvoice(invoice: InvoiceRecord) {
            invoices[invoice.id] = invoice
        }
        override suspend fun updatePostedDescription(invoice: InvoiceRecord, expectedVersion: Int): Boolean {
            invoices[invoice.id] = invoice
            events += "description-update"
            return true
        }
        override suspend fun updateInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) {
            fullUpdateCount += 1
            lastFullUpdate = invoice
            invoices[invoice.id] = invoice
            linesByInvoice[invoice.id] = items.toMutableList()
            events += "full-update"
        }
        override suspend fun addPayment(payment: InvoicePayment): String {
            payments += payment
            return payment.id
        }
        override suspend fun addPaymentAllocation(allocation: InvoicePaymentAllocation) {
            allocations += allocation
        }
        override suspend fun addRealizedFxEvent(event: RealizedFxEvent) {
            fx += event
        }
        override suspend fun deletePayments(invoiceId: String) {
            payments.removeAll { it.invoiceId == invoiceId }
        }
        override suspend fun markVoided(
            invoiceId: String,
            expectedVersion: Int,
            voidedAt: Long,
            reason: String,
            writeId: String,
        ) = true
    }

    private class FakeStock : InvoiceStockPort, InvoiceLinePostingStockPort {
        private val items = mutableMapOf<String, InvoiceStockItem>()
        override suspend fun getAllItems() = items.values.toList()
        override suspend fun getItem(itemId: String) = items[itemId]
        override suspend fun saveItem(item: InvoiceStockItem) {
            items[item.id] = item
        }
        override suspend fun deductStock(
            itemId: String, quantity: Int, invoiceId: String, clientId: String, unitPrice: Double,
            allowNegativeStock: Boolean, sourceWriteId: String,
        ) = Result.success(Unit)
        override suspend fun addStock(
            itemId: String, quantity: Int, invoiceId: String, supplierId: String, unitPrice: Double, sourceWriteId: String,
        ) = Result.success(Unit)
        override suspend fun receivePurchaseAtLatestPrice(command: InvoicePurchaseStockCommand): InvoiceInventoryRevaluationRecord? = null
        override suspend fun deleteMovements(invoiceId: String) = Unit
        override suspend fun reverseMovements(invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun deductPostingLine(mutation: InvoiceSaleStockMutation, identity: InvoicePostingIdentity) = Result.success(Unit)
    }

    private class FakeCash : InvoiceCashPort {
        override suspend fun onSaleCash(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun onPurchaseCash(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun onPaymentReceived(amount: Double, invoiceId: String, sourceWriteId: String) = Unit
        override suspend fun recordMovement(
            type: InvoiceCashMovementType, amount: Double, referenceId: String, note: String, sourceWriteId: String,
        ) = Unit
        override suspend fun reverseMovement(
            type: InvoiceCashMovementType, amount: Double, referenceId: String, note: String, sourceWriteId: String,
        ) = Unit
    }

    private class FakeAuthorization : InvoiceAuthorizationPort {
        var allowPost = true
        override suspend fun canSave(isSale: Boolean, creatingNew: Boolean) = allowPost
        override suspend fun canPost(category: InvoiceCategory) = allowPost
        override suspend fun canEditDescription(category: InvoiceCategory) = true
        override suspend fun canOverrideStock() = false
        override suspend fun canApproveExchangeRate() = true
        override suspend fun canManuallyAllocateLandedCost() = false
        override suspend fun canVoid(category: InvoiceCategory) = true
    }

    private class FakeSyncScheduler(private val events: MutableList<String>) : InvoiceSyncSchedulerPort {
        var calls = 0
        override suspend fun requestSync(organizationId: String, userId: String) {
            calls += 1
            events += "sync"
        }
    }

    private class FakeAtomic(private val events: MutableList<String>) : InvoiceAtomicPersistenceCoordinator {
        val commands = mutableListOf<PersistInvoiceIntegrationCommand>()
        override suspend fun persist(command: PersistInvoiceIntegrationCommand): Result<Unit> {
            commands += command
            events += "integration:${command.writeKind.name}"
            return Result.success(Unit)
        }
        override suspend fun persistVoid(command: PersistInvoiceVoidIntegrationCommand): Result<Unit> = Result.success(Unit)
    }

    private class FakeAudit(private val events: MutableList<String>) : WriteAuditPort {
        override suspend fun write(record: AuditRecord) {
            events += "audit:${record.table.name}"
        }
    }

    private class FakeSession : SessionReader {
        private val user = CurrentUser("user", "User", "")
        private val organization = CurrentOrganization("org")
        override val userId = flowOf(user.id)
        override val userName = flowOf(user.name)
        override val userPhone = flowOf(user.phone)
        override val role = flowOf("admin")
        override val permissionsJson = flowOf("")
        override val organizationId = flowOf(organization.id)
        override val currentUser = flowOf(user)
        override val currentOrganization = flowOf(organization)
        override suspend fun snapshot() = SessionState(user, organization, "admin", "")
    }

    private companion object {
        fun existingDraft(id: String) = baseExisting(id, InvoiceLifecycleStatus.DRAFT)
        fun existingPosted(id: String) = baseExisting(id, InvoiceLifecycleStatus.POSTED)
        fun baseExisting(id: String, lifecycle: InvoiceLifecycleStatus) = InvoiceRecord(
            id = id,
            invoiceNumber = 33,
            clientId = "client",
            organizationId = "org",
            category = InvoiceCategory.SALE,
            description = "Widget",
            totalAmount = 10.0,
            totalAmountMinor = 1_000L,
            transactionCurrencyCode = "SDG",
            functionalCurrencyCode = "SDG",
            transactionAmountMinor = 1_000L,
            invoiceExchangeRateSnapshot = "1",
            exchangeRateTimestamp = 100L,
            exchangeRateSource = "FUNCTIONAL_CURRENCY",
            functionalAmountAtRecognitionMinor = 1_000L,
            legacyCurrencyStatus = com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus.KNOWN,
            createdAt = 100L,
            dueDate = 10_000L,
            status = InvoiceStatus.CLOSED_CREDIT,
            lifecycleStatus = lifecycle,
            lifecycleVersion = 1,
            postedAt = 100L,
        )

        fun existingLine(invoiceId: String) = InvoiceLine(
            id = "old-line",
            invoiceId = invoiceId,
            itemName = "Widget",
            quantity = 1,
            sellPrice = 10.0,
            sellPriceMinor = 1_000L,
            totalPrice = 10.0,
            totalPriceMinor = 1_000L,
            isOwedToMe = true,
        )

        fun assertOrdered(events: List<String>, vararg expected: String) {
            var cursor = -1
            expected.forEach { token ->
                val index = events.withIndex().firstOrNull { it.index > cursor && it.value == token }?.index ?: -1
                assertTrue("Missing or out-of-order event $token in $events", index > cursor)
                cursor = index
            }
        }
    }
}
