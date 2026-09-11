package com.verto.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePendingActionReadModel337Test {
    private lateinit var database: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        sql = database.openHelper.writableDatabase
        sql.execSQL("PRAGMA foreign_keys=OFF")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inventoryProjection_filtersCandidatesAndSeparatesTenants() = runBlocking {
        val cutoff = 1_000L
        insertInventoryItem("low-a", quantity = 0, minQuantity = 5, createdAt = 2_000L)
        insertMovement("mv-low-a", "low-a", "org-a")
        insertInventoryItem("stale-a", quantity = 10, minQuantity = 5, createdAt = 100L)
        insertMovement("mv-stale-a", "stale-a", "org-a")
        insertInventoryItem("fresh-a", quantity = 10, minQuantity = 5, createdAt = 2_000L)
        insertMovement("mv-fresh-a", "fresh-a", "org-a")
        insertInventoryItem("service-a", quantity = 0, minQuantity = 5, createdAt = 100L, isService = true)
        insertMovement("mv-service-a", "service-a", "org-a")
        insertInventoryItem("archived-a", quantity = 0, minQuantity = 5, createdAt = 100L, archived = true)
        insertMovement("mv-archived-a", "archived-a", "org-a")
        insertInventoryItem("low-b", quantity = 0, minQuantity = 5, createdAt = 100L)
        insertMovement("mv-low-b", "low-b", "org-b")

        val a = database.inventoryDao().observePendingActionItems("org-a", cutoff).first().map { it.itemId }.toSet()
        val b = database.inventoryDao().observePendingActionItems("org-b", cutoff).first().map { it.itemId }.toSet()

        assertEquals(setOf("low-a", "stale-a"), a)
        assertEquals(setOf("low-b"), b)
    }

    @Test
    fun invoiceProjection_filtersDueVoidPaidAndTenantRows() = runBlocking {
        val now = 10_000L
        insertClient("client-a")
        insertClient("client-b")
        insertInvoice("due-a", 1, "client-a", "org-a", dueDate = 5_000L, total = 100.0)
        insertInvoice("future-a", 2, "client-a", "org-a", dueDate = 15_000L, total = 100.0)
        insertInvoice("void-a", 3, "client-a", "org-a", dueDate = 5_000L, total = 100.0, voided = true)
        insertInvoice("partial-a", 4, "client-a", "org-a", dueDate = 5_000L, total = 100.0)
        insertPayment("pay-partial", "partial-a", "client-a", 40.0)
        insertInvoice("paid-a", 5, "client-a", "org-a", dueDate = 5_000L, total = 100.0)
        insertPayment("pay-full", "paid-a", "client-a", 100.0)
        insertInvoice("due-b", 6, "client-b", "org-b", dueDate = 5_000L, total = 100.0)

        val a = database.invoiceDao().observeFinancialPendingInvoices("org-a", now, 1, 0).first()
        val b = database.invoiceDao().observeFinancialPendingInvoices("org-b", now, 1, 0).first()

        assertEquals(setOf("due-a", "partial-a"), a.map { it.invoiceId }.toSet())
        assertEquals(40.0, a.single { it.invoiceId == "partial-a" }.totalPaid, 0.0001)
        assertEquals(listOf("due-b"), b.map { it.invoiceId })
    }

    @Test
    fun inactiveCustomerProjection_requiresTenantScopedCustomerRoleAndOldSale() = runBlocking {
        val inactiveCutoff = 10_000L
        val criticalCutoff = 5_000L
        insertClient("inactive-a")
        insertRole("role-a", "inactive-a", "org-a")
        insertInvoice("sale-old-a", 10, "inactive-a", "org-a", createdAt = 1_000L, dueDate = 20_000L)

        insertClient("fresh-a")
        insertRole("role-fresh", "fresh-a", "org-a")
        insertInvoice("sale-fresh-a", 11, "fresh-a", "org-a", createdAt = 12_000L, dueDate = 20_000L)

        insertClient("cross-role")
        insertRole("role-cross-b", "cross-role", "org-b")
        insertInvoice("sale-cross-a", 12, "cross-role", "org-a", createdAt = 1_000L, dueDate = 20_000L)

        insertClient("cash_client_main")
        insertRole("role-cash", "cash_client_main", "org-a")
        insertInvoice("sale-cash", 13, "cash_client_main", "org-a", createdAt = 1_000L, dueDate = 20_000L)

        val a = database.clientDao().observeInactiveCustomerCandidates(
            organizationId = "org-a",
            inactiveCutoffEpochMillis = inactiveCutoff,
            highPriorityCutoffEpochMillis = criticalCutoff,
        ).first()

        assertEquals(listOf("inactive-a"), a.map { it.customerId })
    }

    @Test
    fun shipmentHomeProjections_areTenantScoped_andReceiptIssuesNeedNoAllShipmentLookup() = runBlocking {
        insertShipment("org-a", "ship-a", "A-1", "CUSTOMS")
        insertShipment("org-a", "closed-a", "A-2", "CLOSED")
        insertShipment("org-b", "ship-b", "B-1", "CUSTOMS")
        insertShortage("org-a", "short-a", "ship-a", remaining = 2, detectedAt = 10L)
        insertShortage("org-b", "short-b", "ship-b", remaining = 3, detectedAt = 20L)

        val operationalA = database.logisticsDao().observeHomeOperationalShipments("org-a").first()
        val receiptA = database.logisticsDao().observeHomeReceiptIssues("org-a").first()
        val receiptB = database.logisticsDao().observeHomeReceiptIssues("org-b").first()

        assertEquals(listOf("ship-a"), operationalA.map { it.shipmentId })
        assertEquals(listOf("short-a"), receiptA.map { it.receiptId })
        assertEquals("A-1", receiptA.single().shipmentNumber)
        assertEquals(listOf("short-b"), receiptB.map { it.receiptId })
    }

    private fun insertInventoryItem(
        id: String,
        quantity: Int,
        minQuantity: Int,
        createdAt: Long,
        isService: Boolean = false,
        archived: Boolean = false,
    ) = insertWithDefaults(
        "inventory_items",
        mapOf(
            "id" to id,
            "name" to id,
            "quantity" to quantity,
            "minQuantity" to minQuantity,
            "isService" to if (isService) 1 else 0,
            "is_archived" to if (archived) 1 else 0,
            "createdAt" to createdAt,
            "updatedAt" to createdAt,
        ),
    )

    private fun insertMovement(id: String, itemId: String, organizationId: String) = insertWithDefaults(
        "inventory_movements",
        mapOf(
            "id" to id,
            "itemId" to itemId,
            "organization_id" to organizationId,
            "movementType" to "ADJUST",
            "quantity" to 1,
            "quantityBefore" to 0,
            "quantityAfter" to 1,
            "createdAt" to 100L,
        ),
    )

    private fun insertClient(id: String) = insertWithDefaults(
        "clients",
        mapOf("id" to id, "name" to id, "phone" to "0990000000", "createdAt" to 1L),
    )

    private fun insertRole(id: String, partyId: String, organizationId: String) = insertWithDefaults(
        "party_roles",
        mapOf(
            "id" to id,
            "party_id" to partyId,
            "organization_id" to organizationId,
            "role" to "CUSTOMER",
            "status" to "ACTIVE",
            "created_at" to 1L,
            "updated_at" to 1L,
        ),
    )

    private fun insertInvoice(
        id: String,
        number: Int,
        clientId: String,
        organizationId: String,
        dueDate: Long,
        total: Double = 100.0,
        createdAt: Long = 1_000L,
        voided: Boolean = false,
    ) = insertWithDefaults(
        "invoices",
        mapOf(
            "id" to id,
            "invoiceNumber" to number,
            "clientId" to clientId,
            "organization_id" to organizationId,
            "type" to "GOODS",
            "category" to "SALE",
            "description" to id,
            "totalAmount" to total,
            "createdAt" to createdAt,
            "dueDate" to dueDate,
            "status" to "CLOSED_CREDIT",
            "lifecycle_status" to if (voided) "VOID" else "POSTED",
            "voided" to if (voided) 1 else 0,
        ),
    )

    private fun insertPayment(id: String, invoiceId: String, clientId: String, amount: Double) = insertWithDefaults(
        "payments",
        mapOf(
            "id" to id,
            "invoiceId" to invoiceId,
            "clientId" to clientId,
            "amount" to amount,
            "paymentMethod" to "CASH",
            "paidAt" to 1L,
        ),
    )

    private fun insertShipment(organizationId: String, id: String, number: String, state: String) = insertWithDefaults(
        "logistics_shipments",
        mapOf(
            "organization_id" to organizationId,
            "id" to id,
            "shipment_number" to number,
            "source_location" to "A",
            "destination_location" to "B",
            "state" to state,
            "created_at" to 1L,
        ),
    )

    private fun insertShortage(
        organizationId: String,
        id: String,
        shipmentId: String,
        remaining: Int,
        detectedAt: Long,
    ) = insertWithDefaults(
        "logistics_shortages",
        mapOf(
            "organization_id" to organizationId,
            "id" to id,
            "shipment_id" to shipmentId,
            "shipment_line_id" to "line-$id",
            "original_missing_quantity" to remaining,
            "remaining_missing_quantity" to remaining,
            "detected_at" to detectedAt,
            "request_id" to "request-$id",
        ),
    )

    /** Seeds only test data; production schema/write semantics are untouched. */
    private fun insertWithDefaults(table: String, overrides: Map<String, Any?>) {
        val values = ContentValues()
        sql.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (overrides.containsKey(name)) {
                    put(values, name, overrides[name])
                    continue
                }
                val hasDefault = !cursor.isNull(defaultIndex)
                val required = cursor.getInt(notNullIndex) != 0 || cursor.getInt(pkIndex) != 0
                if (!required || hasDefault) continue
                when (cursor.getString(typeIndex).uppercase()) {
                    "INTEGER" -> values.put(name, 0L)
                    "REAL" -> values.put(name, 0.0)
                    "BLOB" -> values.put(name, ByteArray(0))
                    else -> values.put(name, "")
                }
            }
        }
        overrides.forEach { (name, value) -> if (!values.containsKey(name)) put(values, name, value) }
        val inserted = sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
        assertTrue("Failed to insert into $table", inserted != -1L)
    }

    private fun put(values: ContentValues, key: String, value: Any?) {
        when (value) {
            null -> values.putNull(key)
            is String -> values.put(key, value)
            is Int -> values.put(key, value)
            is Long -> values.put(key, value)
            is Double -> values.put(key, value)
            is Float -> values.put(key, value)
            is Boolean -> values.put(key, if (value) 1 else 0)
            is ByteArray -> values.put(key, value)
            else -> error("Unsupported test value for $key: ${value::class.java.name}")
        }
    }
}
