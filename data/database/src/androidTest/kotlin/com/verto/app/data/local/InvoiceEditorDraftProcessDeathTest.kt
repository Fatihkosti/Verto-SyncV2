package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.InvoiceEditorDraftEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftLineEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceImageEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftRouteColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftModeColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftPersistenceColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftFinancialColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftComposerColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceOwnerColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceVehicleColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftLineValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceEditorDraftProcessDeathTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanupTransientDatabase() {
        context.deleteDatabase(REOPEN_DB)
    }

    @Test
    fun draftSurvivesDatabaseCloseAndReopen() = runBlocking {
        context.deleteDatabase(REOPEN_DB)
        withDb(REOPEN_DB) { db -> seed(db) }
        withDb(REOPEN_DB) { db -> assertSeed(db) }
    }

    /**
     * Device-only process-death phase 1. Run this method, force-stop the target package,
     * then run [phase2_assertDraftAfterRealProcessDeath]. The verifier script documents
     * the exact two-process sequence; this is intentionally not a Rotation/recreate test.
     */
    @Test
    fun phase1_seedDraftBeforeRealProcessDeath() = runBlocking {
        context.deleteDatabase(PROCESS_DEATH_DB)
        withDb(PROCESS_DEATH_DB) { db -> seed(db) }
    }

    /** Phase 2 of the real force-stop test. Do not run standalone. */
    @Test
    fun phase2_assertDraftAfterRealProcessDeath() = runBlocking {
        withDb(PROCESS_DEATH_DB) { db -> assertSeed(db) }
        context.deleteDatabase(PROCESS_DEATH_DB)
    }

    private suspend fun withDb(name: String, block: suspend (AppDatabase) -> Unit) {
        val db = open(name)
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private fun open(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    private suspend fun seed(db: AppDatabase) {
        db.invoiceDraftDao().replaceDraft(
            header = InvoiceEditorDraftEntity(
                draftKey = DRAFT_KEY,
                organizationId = ORG_ID,
                route = InvoiceEditorDraftRouteColumns(
                    routeClientId = "supplier-1",
                    mode = InvoiceEditorDraftModeColumns(true, false, "CREDIT"),
                    selectedClientId = "supplier-1",
                    selectedDateMillis = 1_786_000_000_000L,
                ),
                financial = InvoiceEditorDraftFinancialColumns(
                    dueDays = "14", notes = "process-death-note", paidAmount = "25",
                    dueInstallmentsJson = "[{\"amount\":75.0,\"dueDate\":1787000000000}]",
                    transactionCurrencyCode = "USD", exchangeRate = "2500",
                    persistence = InvoiceEditorDraftPersistenceColumns(
                        writeId = "write-process-death-1", updatedAt = 1_786_000_000_100L,
                    ),
                ),
                composer = InvoiceEditorDraftComposerColumns(
                    draftItemName = "pending-item", draftItemQuantity = "3",
                    draftItemSellPrice = "", draftItemBuyPrice = "100", draftInventoryItemId = "inventory-pending",
                ),
                maintenance = InvoiceEditorDraftMaintenanceColumns(
                    enabled = true, expanded = true,
                    owner = InvoiceEditorDraftMaintenanceOwnerColumns(
                        organizationId = ORG_ID, clientId = "supplier-1",
                        recordId = "maintenance-1", createdAt = 1_786_000_000_000L,
                    ),
                    vehicle = InvoiceEditorDraftMaintenanceVehicleColumns(
                        query = "Hilux", plateNumber = "1234", driverOrDelegate = "driver",
                    ),
                    notes = "maintenance-note",
                ),
            ),
            lines = listOf(
                InvoiceEditorDraftLineEntity(
                    id = "line-1", draftKey = DRAFT_KEY, sortOrder = 0,
                    item = InvoiceEditorDraftLineValues(
                        name = "Brake pad", quantity = "2", sellPrice = "150", buyPrice = "100",
                        itemCategory = "parts", inventoryItemId = "inventory-1",
                    ),
                ),
                InvoiceEditorDraftLineEntity(
                    id = "line-2", draftKey = DRAFT_KEY, sortOrder = 1,
                    item = InvoiceEditorDraftLineValues(
                        name = "Filter", quantity = "4", sellPrice = "80", buyPrice = "50",
                        itemCategory = "parts", inventoryItemId = "inventory-2",
                    ),
                ),
            ),
            images = listOf(
                InvoiceEditorDraftMaintenanceImageEntity(
                    imageId = "image-1",
                    draftKey = DRAFT_KEY,
                    localUri = "content://verto/draft/image-1",
                    mimeType = "image/jpeg",
                    byteSize = 512L,
                    sortOrder = 0,
                )
            ),
        )
    }

    private suspend fun assertSeed(db: AppDatabase) {
        val draft = db.invoiceDraftDao().getDraft(DRAFT_KEY, ORG_ID)
        assertNotNull(draft)
        assertEquals("process-death-note", draft?.financial?.notes)
        assertEquals("write-process-death-1", draft?.financial?.persistence?.writeId)
        assertEquals("[{\"amount\":75.0,\"dueDate\":1787000000000}]", draft?.financial?.dueInstallmentsJson)
        assertEquals("pending-item", draft?.composer?.draftItemName)
        assertEquals(listOf("Brake pad", "Filter"), db.invoiceDraftDao().getLines(DRAFT_KEY).map { it.item.name })
        assertEquals("content://verto/draft/image-1", db.invoiceDraftDao().getImages(DRAFT_KEY).single().localUri)
    }

    private companion object {
        const val ORG_ID = "org-process-death"
        const val DRAFT_KEY = "$ORG_ID:new:purchase:international:none"
        const val REOPEN_DB = "v254-draft-reopen.db"
        const val PROCESS_DEATH_DB = "v254-draft-process-death.db"
    }
}
