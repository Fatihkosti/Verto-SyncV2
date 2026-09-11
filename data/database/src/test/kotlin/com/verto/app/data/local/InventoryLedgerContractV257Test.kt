package com.verto.app.data.local

import com.verto.app.data.local.entity.InventoryCostRevisionEntity
import com.verto.app.data.local.entity.InventoryCostRevisionKind
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryMovementKind
import com.verto.app.data.local.entity.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryLedgerContractV257Test {
    @Test
    fun `schema 73 is connected from 72`() {
        val path = migrationPath(72, 73)
        assertEquals(1, path.size)
        assertEquals(72, path.single().startVersion)
        assertEquals(73, path.single().endVersion)
    }

    @Test
    fun `canonical sale requires source identity and negative signed quantity`() {
        validMovement().copy(sourceId = "").let { row ->
            assertThrows(IllegalArgumentException::class.java) { row.requireCanonicalInventoryContract() }
        }
        validMovement().copy(signedBaseQuantity = 1L).let { row ->
            assertThrows(IllegalArgumentException::class.java) { row.requireCanonicalInventoryContract() }
        }
        assertEquals(-2L, validMovement().requireCanonicalInventoryContract().signedBaseQuantity)
    }

    @Test
    fun `zero quantity is rejected from movement ledger`() {
        val row = validMovement().copy(signedBaseQuantity = 0L)
        assertThrows(IllegalArgumentException::class.java) { row.requireCanonicalInventoryContract() }
    }

    @Test
    fun `reversal must identify exactly one original`() {
        val invalid = validMovement().copy(
            movementKind = InventoryMovementKind.REVERSAL,
            signedBaseQuantity = 2L,
            reversesMovementId = null,
        )
        assertThrows(IllegalArgumentException::class.java) { invalid.requireCanonicalInventoryContract() }
        invalid.copy(reversesMovementId = "movement-original").requireCanonicalInventoryContract()
    }

    @Test
    fun `cost revision is quantity independent and validates identity`() {
        validCostRevision().requireCanonicalCostContract()
        assertThrows(IllegalArgumentException::class.java) {
            validCostRevision().copy(idempotencyKey = "").requireCanonicalCostContract()
        }
    }

    private fun validMovement() = InventoryMovementEntity(
        id = "movement-1",
        itemId = "item-1",
        movementType = MovementType.OUT,
        quantity = 2,
        quantityBefore = 10,
        quantityAfter = 8,
        sourceType = "INVOICE",
        sourceId = "invoice-1",
        organizationId = "org-1",
        movementKind = InventoryMovementKind.SALE,
        signedBaseQuantity = -2L,
        sourceLineId = "line-1",
        commandId = "command-1",
        idempotencyKey = "sale:invoice-1:line-1",
        postingGroupId = "invoice-1",
        occurredAt = 100L,
        recordedAt = 101L,
        createdBy = "user-1",
        deviceId = "device-1",
        contractVersion = INVENTORY_LEDGER_CONTRACT_VERSION,
    )

    private fun validCostRevision() = InventoryCostRevisionEntity(
        costRevisionId = "cost-1",
        organizationId = "org-1",
        itemId = "item-1",
        sourceType = "PURCHASE_INVOICE",
        sourceId = "purchase-1",
        sourceLineId = "line-1",
        revisionKind = InventoryCostRevisionKind.LOCAL_PURCHASE_APPROVED,
        directPurchaseCostMinor = 10000L,
        landedCostPerBaseUnitMinor = 0L,
        approvedInventoryCostMinor = 10000L,
        currencyCode = "SDG",
        exchangeRateSnapshot = "1.000000",
        commandId = "command-cost-1",
        idempotencyKey = "cost:purchase-1:line-1",
        approvedAt = 100L,
        recordedAt = 101L,
        createdBy = "user-1",
        deviceId = "device-1",
    )
}
