package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.MovementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** v256 characterization only: records the inventory behavior present in v255. */
@RunWith(AndroidJUnit4::class)
class InventoryBaselineF256Test {
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
    fun opening_quantity_is_currently_saved_as_snapshot_without_movement() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "opening", name = "Opening", quantity = 7))
        assertEquals(7, db.inventoryDao().getItemByIdSync("opening")?.quantity)
        assertTrue(db.inventoryDao().getAllMovementsSync().isEmpty())
    }

    @Test
    fun sale_and_sales_return_preserve_current_latest_purchase_price() = runTest {
        db.inventoryDao().insertItem(
            InventoryItemEntity(id = "sale", name = "Sale", quantity = 10, buyPrice = 100.0, buyPriceMinor = 10_000L)
        )
        db.inventoryDao().deductStockAtomic(
            itemId = "sale", quantity = 3, invoiceId = "sale-invoice", clientId = "client",
            unitPrice = 150.0, sourceWriteId = "sale-write",
        ).getOrThrow()
        db.inventoryDao().restoreSalesReturnAtomic(
            itemId = "sale", quantity = 1, returnId = "return-1", returnLineId = "return-line-1",
            clientId = "client", historicalUnitCostMinor = 10_000L, occurredAt = 256L, writeId = "return-write",
        )
        val item = db.inventoryDao().getItemByIdSync("sale")
        assertEquals(8, item?.quantity)
        assertEquals(10_000L, item?.buyPriceMinor)
        assertEquals(listOf(MovementType.OUT, MovementType.IN), db.inventoryDao().getAllMovementsSync().map { it.movementType })
    }

    @Test
    fun shipment_receipt_retry_with_same_posting_id_is_idempotent() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "shipment-item", name = "Shipment", quantity = 2))
        repeat(2) {
            db.inventoryDao().receiveShipmentStockAtomic(
                organizationId = "org-256", actorId = "actor-256",
                postingId = "posting-256", shipmentId = "shipment-256", receivingBatchId = "batch-256",
                receivingLineId = "line-256", itemId = "shipment-item", quantity = 4,
                supplierId = "supplier", unitPrice = 50.0,
            ).getOrThrow()
        }
        assertEquals(6, db.inventoryDao().getItemByIdSync("shipment-item")?.quantity)
        assertEquals(1, db.inventoryDao().getAllMovementsSync().count { it.id == "posting-256" })
    }

    @Test
    fun concurrent_sales_of_last_unit_allow_exactly_one_success() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "concurrent", name = "Concurrent", quantity = 1))
        val results = coroutineScope {
            listOf("a", "b").map { writeId ->
                async(Dispatchers.IO) {
                    db.inventoryDao().deductStockAtomic(
                        itemId = "concurrent", quantity = 1, invoiceId = "", clientId = "client",
                        unitPrice = 1.0, sourceWriteId = writeId,
                    ).isSuccess
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(0, db.inventoryDao().getItemByIdSync("concurrent")?.quantity)
    }

    @Test
    fun repeated_low_level_sale_write_id_currently_does_not_deduplicate() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "retry", name = "Retry", quantity = 5))
        repeat(2) {
            db.inventoryDao().deductStockAtomic(
                itemId = "retry", quantity = 1, invoiceId = "invoice-retry", clientId = "client",
                unitPrice = 10.0, sourceWriteId = "same-write-id",
            ).getOrThrow()
        }
        // Baseline defect intentionally characterized for v257+: writeId is metadata, not a uniqueness guard yet.
        assertEquals(3, db.inventoryDao().getItemByIdSync("retry")?.quantity)
        assertEquals(2, db.inventoryDao().getMovementsByInvoice("invoice-retry").size)
    }
}
