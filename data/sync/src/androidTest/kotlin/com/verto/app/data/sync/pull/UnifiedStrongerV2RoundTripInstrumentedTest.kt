package com.verto.app.data.sync.pull

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.ownership.SyncPendingProtection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** B08 SQL response fixtures decoded through the production receiver into real Room tables. */
@RunWith(AndroidJUnit4::class)
class UnifiedStrongerV2RoundTripInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var applier: UnifiedStrongerSyncChangeApplier
    private val json = Json { explicitNulls = true }

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val protection = SyncPendingProtection(database)
        applier = UnifiedStrongerSyncChangeApplier(
            database,
            FinancialMaterializerV2(database, protection, FinancialEffectVerifierV2(database, protection)),
        )
        runBlocking {
            database.inventoryDao().insertItem(InventoryItemEntity(id = "item-1", name = "B08 fixture", isDirty = false))
        }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun legalServerPayloadsMaterializeWithoutInventedMoneyOrOrdering() = runBlocking {
        applier.apply(change("CLIENT_CREDIT", "credit-1", """{
          "materialization":{"id":"credit-1","clientId":"client-1","amountMinor":12345,"note":"","sourcePaymentId":"payment-1","createdAt":100,"employeeId":"","employeeName":""}
        }"""))
        val credit = database.clientCreditDao().getCreditByIdSync("credit-1")!!
        assertEquals(12345L, credit.amountMinor)
        assertEquals(123.45, credit.amount, 0.0)

        applier.apply(change("INVENTORY_MOVEMENT", "movement-1", """{
          "movement":{"itemId":"item-1","invoiceId":"","clientId":"","movementType":"IN","quantity":2,"quantityBefore":4,"quantityAfter":6,"movementKind":"PURCHASE","signedBaseQuantity":2,"unitPrice":999999.0,"unitPriceMinor":150,"note":"","shipmentId":"","sourceType":"PURCHASE","sourceId":"source-1","sourceLineId":null,"sourceVersion":1,"writeId":"write-1","commandId":"command-1","idempotencyKey":"idem-1","postingGroupId":null,"reversesMovementId":null,"conversionFactorSnapshot":"1","occurredAt":101,"recordedAt":102,"serverAcceptedAt":103,"serverSequence":7,"createdBy":null,"deviceId":"device-1","contractVersion":2,"createdAt":100}
        }""", revision = 7))
        val movement = database.inventoryDao().getMovementById("movement-1")!!
        assertEquals(150L, movement.unitPriceMinor)
        assertEquals(1.5, movement.unitPrice, 0.0)
        assertEquals(7L, movement.serverSequence)
        assertEquals(102L, movement.recordedAt)
        assertEquals(103L, movement.serverAcceptedAt)
        assertNull(movement.createdBy)

        applier.apply(change("INVENTORY_COST_REVISION", "cost-1", """{
          "costRevision":{"itemId":"item-1","sourceType":"PURCHASE","sourceId":"source-1","sourceLineId":null,"revisionKind":"LOCAL_PURCHASE_APPROVED","directPurchaseCostMinor":100,"landedCostPerBaseUnitMinor":120,"approvedInventoryCostMinor":120,"currencyCode":"USD","exchangeRateSnapshot":"1","allocationBasis":"","allocationResidualMinor":0,"isProvisional":false,"reversesCostRevisionId":null,"commandId":"command-2","idempotencyKey":"idem-2","costSequence":8,"approvedAt":104,"recordedAt":105,"createdBy":"server","deviceId":"device-1","contractVersion":2}
        }""", revision = 8))
        val cost = database.inventoryDao().getCostRevisionById("cost-1")!!
        assertEquals(120L, cost.approvedInventoryCostMinor)
        assertEquals(8L, cost.costSequence)
        assertEquals(105L, cost.recordedAt)
    }

    @Test
    fun missingMinorOrServerOrderingFailsClosedWithoutRoomWrite() = runBlocking {
        val badCredit = runCatching {
            applier.apply(change("CLIENT_CREDIT", "credit-bad", """{
              "materialization":{"id":"credit-bad","clientId":"client-1","amount":123.45,"note":"","sourcePaymentId":"payment-1","createdAt":100,"employeeId":"","employeeName":""}
            }"""))
        }
        val badMovement = runCatching {
            applier.apply(change("INVENTORY_MOVEMENT", "movement-bad", """{
              "movement":{"itemId":"item-1","invoiceId":"","clientId":"","movementType":"IN","quantity":2,"quantityBefore":0,"quantityAfter":2,"movementKind":"PURCHASE","signedBaseQuantity":2,"unitPriceMinor":150,"note":"","shipmentId":"","sourceType":"PURCHASE","sourceId":"source-1","sourceLineId":null,"sourceVersion":1,"writeId":"write-1","commandId":"command-1","idempotencyKey":"idem-1","postingGroupId":null,"reversesMovementId":null,"conversionFactorSnapshot":"1","occurredAt":101,"recordedAt":102,"serverAcceptedAt":103,"createdBy":null,"deviceId":"device-1","contractVersion":2,"createdAt":100}
            }"""))
        }
        assertEquals(true, badCredit.isFailure)
        assertEquals(true, badMovement.isFailure)
        assertNull(database.clientCreditDao().getCreditByIdSync("credit-bad"))
        assertNull(database.inventoryDao().getMovementById("movement-bad"))
    }

    private fun change(type: String, id: String, payload: String, revision: Long = 1) = SyncChange(
        revision = revision,
        organizationId = "org",
        syncScopeId = "scope",
        aggregateType = type,
        aggregateId = id,
        operationType = SyncMutationOperation.UPSERT,
        entityVersion = revision,
        payloadVersion = 2,
        payload = json.parseToJsonElement(payload) as JsonObject,
        transactionId = "tx-$revision-$id",
        transactionOrder = 0,
        transactionSize = 1,
        changedAtEpochMillis = 1000 + revision,
    )
}
