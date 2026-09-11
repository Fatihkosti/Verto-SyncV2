package com.verto.app.data.operations.transaction

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceType
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.sync.FrozenMutationStore
import com.verto.app.data.sync.SyncBatchCoordinatorV2
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.feature.invoice.domain.model.InvoiceIntegrationWriteKind
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialOutboxWriterV2InstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var writer: FinancialOutboxWriter

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val store = FrozenMutationStore(database, SyncPendingProtection(database))
        writer = FinancialOutboxWriter(
            database, FinancialSnapshotFactoryV2(database), store, SyncBatchCoordinatorV2(database, store),
        )
    }

    @After fun closeDatabase() = database.close()

    @Test fun fullSnapshotPacketReferencesAndBatchCommitTogether() = runBlocking {
        database.withTransaction {
            insertAggregate("invoice-1", "line-1")
            writer.appendInvoice(command("invoice-1", "write-1"))
        }

        assertEquals(1L, count("financial_outbox"))
        assertEquals(1L, count("sync_mutation_packet"))
        assertEquals(2L, count("sync_local_generation"))
        assertEquals(2L, count("sync_pending_reference"))
        assertEquals(1L, count("sync_write_batch"))
        assertEquals(1L, count("sync_write_batch_member"))
    }

    @Test fun producerFailureRollsBackDomainSnapshotPacketReferencesAndBatch() = runBlocking {
        runCatching {
            database.withTransaction {
                insertAggregate("invoice-rollback", "line-rollback")
                writer.appendInvoice(command("invoice-rollback", "write-rollback"))
                error("force rollback")
            }
        }

        listOf("clients", "invoices", "invoice_items", "financial_outbox", "sync_mutation_packet",
            "sync_local_generation", "sync_pending_reference", "sync_write_batch", "sync_write_batch_member")
            .forEach { table -> assertEquals("rollback left rows in $table", 0L, count(table)) }
    }

    private suspend fun insertAggregate(invoiceId: String, lineId: String) {
        database.clientDao().insertClient(PartyIdentityEntity(id = "client-$invoiceId", name = "Client", phone = "249"))
        database.invoiceDao().insertInvoice(InvoiceEntity(
            id = invoiceId, invoiceNumber = 1, clientId = "client-$invoiceId", organizationId = "org-1",
            type = InvoiceType.GOODS, category = InvoiceCategory.SALE, description = "Invoice",
            totalAmount = 10.0, totalAmountMinor = 1_000, transactionCurrencyCode = "SDG",
            functionalCurrencyCode = "SDG", transactionAmountMinor = 1_000,
            invoiceExchangeRateSnapshot = "1", exchangeRateTimestamp = 1, exchangeRateSource = "LOCAL",
            functionalAmountAtRecognitionMinor = 1_000, legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
            createdAt = 1, dueDate = 2, createdBy = "user-1",
        ))
        database.invoiceDao().insertInvoiceItems(listOf(InvoiceItemEntity(
            id = lineId, invoiceId = invoiceId, itemName = "Item", quantity = 1,
            sellPrice = 10.0, sellPriceMinor = 1_000, totalPrice = 10.0, totalPriceMinor = 1_000,
            unitSellPrice = 10.0, unitSellPriceMinor = 1_000,
            lineRevenueSnapshot = 10.0, lineRevenueSnapshotMinor = 1_000,
            costSnapshotStatus = "KNOWN",
        )))
    }

    private fun command(invoiceId: String, writeId: String) = PersistInvoiceIntegrationCommand(
        writeId, "org-1", invoiceId, "client-$invoiceId", InvoiceIntegrationWriteKind.CREATED,
        isSale = true, companyClient = false, maintenance = null, occurredAt = 10,
    )

    private fun count(table: String): Long = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
}
