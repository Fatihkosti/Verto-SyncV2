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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerPartyV2TenantIsolation387Test {
    private lateinit var database: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        sql = database.openHelper.writableDatabase
    }

    @After fun tearDown() = database.close()

    @Test fun customerList_isStrictlyTenantScopedByActivePartyRole() = runBlocking {
        insertClient("a")
        insertClient("b")
        insertRole("ra", "a", "org-a", "CUSTOMER")
        insertRole("rb", "b", "org-b", "CUSTOMER")
        insertCustomerProfile("a", "org-a", "INDIVIDUAL")
        insertCustomerProfile("b", "org-b", "COMPANY")

        assertEquals(listOf("a"), database.clientDao().getAllClientsForOrganization("org-a").first().map { it.id })
        assertEquals(listOf("b"), database.clientDao().getAllClientsForOrganization("org-b").first().map { it.id })
    }

    @Test fun sameParty_canHaveDifferentCustomerProfilesPerOrganizationWithoutLeakage() = runBlocking {
        insertClient("shared")
        insertRole("ra", "shared", "org-a", "CUSTOMER")
        insertRole("rb", "shared", "org-b", "CUSTOMER")
        insertCustomerProfile("shared", "org-a", "MARKETER")
        insertCustomerProfile("shared", "org-b", "COMPANY")

        assertEquals("MARKETER", database.clientDao().getClientRoleProjectionSync("org-a", "shared")?.customerSegment)
        assertEquals("COMPANY", database.clientDao().getClientRoleProjectionSync("org-b", "shared")?.customerSegment)
    }

    @Test fun customerBalance_ignoresInvoicesAndPaymentsFromOtherOrganization() = runBlocking {
        insertClient("shared")
        insertRole("ra", "shared", "org-a", "CUSTOMER")
        insertRole("rb", "shared", "org-b", "CUSTOMER")
        insertCustomerProfile("shared", "org-a", "INDIVIDUAL")
        insertCustomerProfile("shared", "org-b", "INDIVIDUAL")
        insertInvoice("ia", "shared", "org-a", 100.0)
        insertInvoice("ib", "shared", "org-b", 900.0)
        insertPayment("pa", "ia", "shared", 40.0)
        insertPayment("pb", "ib", "shared", 800.0)

        val a = database.clientDao().getAllClientsWithBalance("org-a").first().single { it.client.id == "shared" }
        val b = database.clientDao().getAllClientsWithBalance("org-b").first().single { it.client.id == "shared" }
        assertEquals(100.0, a.totalDebt, 0.0)
        assertEquals(40.0, a.totalPaid, 0.0)
        assertEquals(60.0, a.remaining, 0.0)
        assertEquals(900.0, b.totalDebt, 0.0)
        assertEquals(800.0, b.totalPaid, 0.0)
        assertEquals(100.0, b.remaining, 0.0)
    }

    @Test fun archivedCustomerRole_disappearsFromCustomerReads() = runBlocking {
        insertClient("archived")
        insertRole("r", "archived", "org-a", "CUSTOMER", status = "ARCHIVED")
        insertCustomerProfile("archived", "org-a", "INDIVIDUAL")

        assertEquals(emptyList<String>(), database.clientDao().getAllClientsForOrganization("org-a").first().map { it.id })
        assertNull(database.clientDao().getClientRoleProjectionSync("org-a", "archived"))
    }

    private fun insertClient(id: String) = insertWithDefaults(
        "clients", mapOf("id" to id, "name" to id, "phone" to "0990000000", "createdAt" to 1L)
    )

    private fun insertRole(id: String, partyId: String, org: String, role: String, status: String = "ACTIVE") = insertWithDefaults(
        "party_roles", mapOf(
            "id" to id, "party_id" to partyId, "organization_id" to org,
            "role" to role, "status" to status, "created_at" to 1L, "updated_at" to 1L,
        )
    )

    private fun insertCustomerProfile(partyId: String, org: String, segment: String) = insertWithDefaults(
        "customer_profiles", mapOf(
            "organization_id" to org, "party_id" to partyId, "segment" to segment, "updated_at" to 1L,
        )
    )

    private fun insertInvoice(id: String, clientId: String, org: String, amount: Double) = insertWithDefaults(
        "invoices", mapOf(
            "id" to id, "invoiceNumber" to id.hashCode(), "clientId" to clientId,
            "organization_id" to org, "type" to "GOODS", "category" to "SALE",
            "description" to id, "totalAmount" to amount, "createdAt" to 1L,
            "dueDate" to Long.MAX_VALUE, "status" to "CLOSED_CREDIT", "lifecycle_status" to "POSTED", "voided" to 0,
        )
    )

    private fun insertPayment(id: String, invoiceId: String, clientId: String, amount: Double) = insertWithDefaults(
        "payments", mapOf(
            "id" to id, "invoiceId" to invoiceId, "clientId" to clientId,
            "amount" to amount, "paymentMethod" to "CASH", "paidAt" to 1L,
        )
    )

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
                if (overrides.containsKey(name)) { put(values, name, overrides[name]); continue }
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
        assertTrue(sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L)
    }

    private fun put(values: ContentValues, key: String, value: Any?) = when (value) {
        null -> values.putNull(key)
        is String -> values.put(key, value)
        is Int -> values.put(key, value)
        is Long -> values.put(key, value)
        is Double -> values.put(key, value)
        is Boolean -> values.put(key, if (value) 1 else 0)
        is ByteArray -> values.put(key, value)
        else -> error("Unsupported test value for $key: ${value::class.java.name}")
    }
}
