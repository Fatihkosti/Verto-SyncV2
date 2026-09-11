package com.verto.app.feature.inventory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InventoryStockOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryStockWriterV259Test {
    private lateinit var db: AppDatabase
    private lateinit var writer: InventoryStockWriter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        writer = InventoryStockWriter(
            database = db,
            inventoryDao = db.inventoryDao(),
            sessionReader = FakeSessionReader,
            authorization = object : InventoryStockWriteAuthorization {
                override suspend fun canAdjust() = true
            },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun twentyConcurrentIssues_haveNoLostUpdate_andRetryIsIdempotent() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "item", name = "Part", quantity = 100))

        (0 until 20).map { index ->
            async(Dispatchers.IO) {
                writer.issue(
                    request = InventoryIssueRequest("item", 1, "invoice-$index", "client", 10.0, false),
                    meta = InventoryWriteMeta("cmd-$index", "sale"),
                ).getOrThrow()
            }
        }.awaitAll()

        assertEquals(80, db.inventoryDao().getItemByIdSync("item")!!.quantity)
        assertEquals(20, db.inventoryDao().getPendingInventoryStockOutbox("org").size)

        writer.issue(
            request = InventoryIssueRequest("item", 1, "invoice-0", "client", 10.0, false),
            meta = InventoryWriteMeta("cmd-0", "retry"),
        ).getOrThrow()

        assertEquals(80, db.inventoryDao().getItemByIdSync("item")!!.quantity)
        assertEquals(20, db.inventoryDao().getPendingInventoryStockOutbox("org").size)
    }

    @Test
    fun movementInsertFailure_rollsBackSnapshotAndCommandGuard() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "item", name = "Part", quantity = 5))
        db.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER v259_abort_inventory_movement BEFORE INSERT ON inventory_movements
               BEGIN SELECT RAISE(ABORT, 'forced movement failure'); END""",
        )

        val result = writer.issue(
            request = InventoryIssueRequest("item", 1, "invoice-fail", "client", 10.0, false),
            meta = InventoryWriteMeta("cmd-movement-fail", "sale"),
        )

        assertTrue(result.isFailure)
        assertEquals(5, db.inventoryDao().getItemByIdSync("item")!!.quantity)
        assertNull(db.inventoryDao().getInventoryWriteGuard("org", "cmd-movement-fail"))
    }

    @Test
    fun outboxFailure_rollsBackSnapshotMovementAndCommandGuard() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "item", name = "Part", quantity = 5))
        val postingId = "posting-1"
        db.inventoryDao().insertInventoryStockOutbox(
            InventoryStockOutboxEntity().apply {
                id = "preexisting"
                organizationId = "org"
                commandId = "other-command"
                idempotencyKey = "other-command:preexisting"
                movementId = postingId
                itemId = "item"
                operation = "TEST"
                signedBaseQuantity = 0
                createdAt = 1L
            },
        )

        val result = writer.receiveShipment(
            request = ShipmentStockReceiptRequest(
                postingId, "shipment-1", "batch-1", "line-1", "item", 2,
            ),
            details = InventoryReceiptDetails("supplier", 20.0, "receipt"),
        )

        assertTrue(result.isFailure)
        assertEquals(5, db.inventoryDao().getItemByIdSync("item")!!.quantity)
        assertTrue(db.inventoryDao().getMovementsByWriteId(postingId).isEmpty())
        assertNull(db.inventoryDao().getInventoryWriteGuard("org", postingId))
    }

    @Test
    fun manualAdjustment_requiresPermissionAndReason() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id = "item", name = "Part", quantity = 5))
        val denied = InventoryStockWriter(
            database = db,
            inventoryDao = db.inventoryDao(),
            sessionReader = FakeSessionReader,
            authorization = object : InventoryStockWriteAuthorization {
                override suspend fun canAdjust() = false
            },
        )

        assertTrue(denied.adjust("item", 7, "سبب", "adjust-denied").isFailure)
        assertTrue(writer.adjust("item", 7, "", "adjust-blank").isFailure)
        assertEquals(5, db.inventoryDao().getItemByIdSync("item")!!.quantity)
    }

    private object FakeSessionReader : SessionReader {
        private val user = CurrentUser(id = "user", name = "Tester")
        private val organization = CurrentOrganization(id = "org")
        override val userId: Flow<String> = flowOf(user.id)
        override val userName: Flow<String> = flowOf(user.name)
        override val userPhone: Flow<String> = flowOf("")
        override val role: Flow<String> = flowOf("admin")
        override val permissionsJson: Flow<String> = flowOf("")
        override val organizationId: Flow<String> = flowOf(organization.id)
        override val currentUser: Flow<CurrentUser> = flowOf(user)
        override val currentOrganization: Flow<CurrentOrganization> = flowOf(organization)
        override suspend fun snapshot(): SessionState = SessionState(user = user, organization = organization, role = "admin")
    }
}
