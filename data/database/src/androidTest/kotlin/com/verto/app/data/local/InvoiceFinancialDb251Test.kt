package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.ClientCreditEntity
import com.verto.app.data.local.entity.InvoiceWriteGuardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceFinancialDb251Test {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun duplicate_write_identity_is_idempotent() = runTest {
        val guard = InvoiceWriteGuardEntity(
            id = "guard-1",
            organizationId = "org",
            operationType = "POST_INVOICE",
            writeId = "write-251",
            targetInvoiceId = "invoice-1",
        )
        assertTrue(db.invoiceDao().insertInvoiceWriteGuard(guard) > 0L)
        assertEquals(-1L, db.invoiceDao().insertInvoiceWriteGuard(guard.copy(id = "guard-2")))
        assertNotNull(db.invoiceDao().getInvoiceWriteGuard("org", "POST_INVOICE", "write-251"))
    }

    @Test
    fun mid_transaction_failure_rolls_back_financial_guard() = runTest {
        runCatching {
            db.withTransaction {
                db.invoiceDao().insertInvoiceWriteGuard(
                    InvoiceWriteGuardEntity(
                        id = "guard-rollback",
                        organizationId = "org",
                        operationType = "POST_INVOICE",
                        writeId = "write-rollback",
                        targetInvoiceId = "invoice-rollback",
                    )
                )
                error("injected failure")
            }
        }
        assertNull(db.invoiceDao().getInvoiceWriteGuard("org", "POST_INVOICE", "write-rollback"))
    }

    @Test
    fun concurrent_sales_of_last_item_allow_exactly_one_stock_deduction() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "last-item", name = "Last item", quantity = 1))

        val results = coroutineScope {
            listOf("sale-a", "sale-b").map { writeId ->
                async(Dispatchers.IO) {
                    db.inventoryDao().deductStockAtomic(
                        itemId = "last-item",
                        quantity = 1,
                        invoiceId = "",
                        clientId = "client",
                        unitPrice = 10.0,
                        sourceWriteId = writeId,
                    ).isSuccess
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it })
        assertEquals(0, db.inventoryDao().getItemByIdSync("last-item")?.quantity)
    }

    @Test
    fun local_purchase_reprices_entire_balance_to_last_purchase_price_without_average() = runTest {
        db.inventoryDao().insertItem(
            InventoryItemEntity(
                id = "repriced-item",
                name = "Repriced item",
                quantity = 10,
                buyPrice = 100.0,
                buyPriceMinor = 10_000L,
            )
        )
        val event = db.inventoryDao().receivePurchaseAtLatestPriceAtomic(
            organizationId = "org-251",
            itemId = "repriced-item",
            quantity = 10,
            invoiceId = "purchase-251",
            supplierId = "supplier",
            buyPriceMinor = 20_000L,
            sellPriceMinor = null,
            actorId = "tester",
            actorName = "Tester",
            occurredAt = 251L,
            writeId = "purchase-write-251",
            eventId = "revaluation-251",
        )
        val after = db.inventoryDao().getItemByIdSync("repriced-item")
        assertEquals(20, after?.quantity)
        assertEquals(20_000L, after?.buyPriceMinor)
        assertEquals(100_000L, event?.revaluationDifferenceMinor)
    }

    @Test
    fun advance_credit_sum_uses_fixed_point_minor_units() = runTest {
        val dao = db.clientCreditDao()
        assertTrue(dao.insert(ClientCreditEntity(id = "credit-a", clientId = "client", amount = 0.1)) > 0L)
        assertTrue(dao.insert(ClientCreditEntity(id = "credit-b", clientId = "client", amount = 0.2)) > 0L)
        assertEquals(30L, dao.getNetCreditMinorForClientSync("client"))
    }

    @Test
    fun duplicate_outbox_identity_is_stored_once() = runTest {
        val event = FinancialOutboxEntity(
            eventId = "event-1",
            organizationId = "org",
            writeId = "write-1",
            aggregateId = "invoice-1",
            aggregateVersion = 1,
            sequence = 1,
            operationType = "POST_INVOICE",
            payload = "{}",
            occurredAt = 1L,
        )
        assertTrue(db.invoiceDao().insertFinancialOutbox(event) > 0L)
        assertEquals(-1L, db.invoiceDao().insertFinancialOutbox(event.copy(eventId = "event-2")))
        assertEquals("event-1", db.invoiceDao().getFinancialOutboxByIdentity("org", "POST_INVOICE", "write-1")?.eventId)
    }
}
