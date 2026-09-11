package com.verto.app.data.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSyncContractV2Test {
    @Test fun `exact v2 serialization keeps explicit null and all full lists`() {
        val snapshot = completeSnapshot()
        val wire = SyncContractV2Codec.encode(snapshot)
        val decoded = SyncContractV2Codec.json.parseToJsonElement(wire).jsonObject
        assertEquals(JsonNull, decoded["expectedFinancialStreamVersion"])
        assertTrue(wire.contains("\"contractVersion\":2").not())
        listOf("items", "dueInstallments", "payments", "paymentAllocations", "realizedFxEvents",
            "returnDocuments", "returnLines", "returnPaymentAllocations", "explicitTombstones",
            "effectReferences").forEach { assertTrue("missing $it", decoded.containsKey(it)) }
        assertFalse(wire.contains("totalAmount\""))
        assertFalse(wire.contains("imageUri"))
        assertEquals(FINANCIAL_GOLDEN_SHA256, sha256(wire))
        SyncContractV2Codec.requireValid(snapshot)
    }

    @Test fun `item-only semantic change changes business hash even when count and total stay fixed`() {
        val first = completeSnapshot()
        val changedWithoutHash = first.copy(
            items = first.items.map { it.copy(itemName = "changed line") },
            businessContentHash = ZERO,
        )
        val changed = changedWithoutHash.copy(
            businessContentHash = SyncContractV2Codec.financialBusinessHash(changedWithoutHash),
        )
        assertNotEquals(first.businessContentHash, changed.businessContentHash)
        SyncContractV2Codec.requireValid(changed)
    }

    @Test fun `transport versions do not change semantic hash`() {
        val first = completeSnapshot()
        val changed = first.copy(financialStreamVersion = 9, expectedFinancialStreamVersion = 8)
        assertEquals(first.businessContentHash, SyncContractV2Codec.financialBusinessHash(changed))
    }

    @Test fun `missing identity unknown contract and overflow fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncContractV2Codec.requireValid(validMovement().copy(deviceId = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncContractV2Codec.requireValid(validMovement().copy(contractVersion = 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncContractV2Codec.checkedMultiply(Long.MAX_VALUE, 2, "quantityCost")
        }
    }

    @Test fun `movement cost and credit use camelCase minor fields`() {
        val movement = validMovement()
        SyncContractV2Codec.requireValid(movement)
        val movementWire = SyncContractV2Codec.encode(movement)
        assertTrue(movementWire.contains("\"signedBaseQuantity\":2"))
        assertTrue(movementWire.contains("\"unitPriceMinor\":150"))
        assertFalse(movementWire.contains("signed_base_quantity"))

        val cost = InventoryCostRevisionDtoV2("cost-1", "org-1", "item-1", "PURCHASE", "source-1", null,
            "APPROVAL", 100, 110, 110, "SDG", "1.0", "VALUE", 0, false, null,
            "cmd-1", "idem-1", null, 10, 11, "user-1", "device-1", 2)
        SyncContractV2Codec.requireValid(cost)
        assertTrue(SyncContractV2Codec.encode(cost).contains("\"approvedInventoryCostMinor\":110"))

        val credit = ClientCreditDtoV2("credit-1", "client-1", 500, "note", "payment-1", 12, "employee-1", "Employee")
        SyncContractV2Codec.requireValid(credit)
        assertFalse(SyncContractV2Codec.encode(credit).contains("\"amount\":"))
    }

    @Test fun `purchase snapshot is typed complete and never serializes a private uri`() {
        val request = PurchaseRequestDtoV2(
            purchaseOrders = listOf(PurchaseOrderDtoV2("po-1", "org-1", "PO-1", "supplier-1", "LOCAL",
                "SDG", "RECEIVED", 10, null, "user-1", "User", null, null, "", "write-1")),
            purchaseOrderLines = listOf(PurchaseOrderLineDtoV2("pol-1", "po-1", 1, "item-1", "Item", 2, 50)),
            goodsReceipts = listOf(GoodsReceiptDtoV2("gr-1", "org-1", "po-1", "GR-1", 11,
                "user-1", "User", "", "write-2")),
            goodsReceiptLines = listOf(GoodsReceiptLineDtoV2("grl-1", "gr-1", "pol-1", "item-1", 2, 2, 0, 50)),
            attachments = listOf(PurchaseAttachmentDtoV2("application/pdf", "receipt.pdf", "LOCAL_PENDING")),
            matches = emptyList(), matchLines = emptyList(), allocations = emptyList(), paymentOverrides = emptyList(),
        )
        SyncContractV2Codec.requireValid(request, "org-1")
        val wire = SyncContractV2Codec.encode(request)
        assertTrue(wire.contains("\"goodsReceiptLines\""))
        assertFalse(wire.contains("privateUri"))
    }

    @Test fun `legacy owner310 cost allocation keeps the established finite double shape`() {
        val allocation = CostAllocationDtoV2("ca-1", "item-1", "SHIPMENT_COST", "shipment-1",
            12.5, 2.5, 5, "BY_QUANTITY", "", 12)
        SyncContractV2Codec.requireValid(allocation)
        val wire = SyncContractV2Codec.encode(allocation)
        assertTrue(wire.contains("\"allocatedAmount\":12.5"))
        assertFalse(wire.contains("allocatedAmountMinor"))
        assertThrows(IllegalArgumentException::class.java) {
            SyncContractV2Codec.requireValid(allocation.copy(perUnitCost = Double.NaN))
        }
    }

    private fun completeSnapshot(): FinancialAggregateSnapshotV2 {
        val draft = FinancialAggregateSnapshotV2(
            organizationId = "org-1", invoiceId = "invoice-1", financialStreamVersion = 2,
            expectedFinancialStreamVersion = null,
            header = InvoiceDtoV2("invoice-1", 7, "client-1", "org-1", null, null, "GOODS", "SALE",
                "invoice", 1000, "SDG", "SDG", 1000, "1.0", "FUNCTIONAL_PER_TRANSACTION",
                1, "LOCAL", 1000, "KNOWN", 10, 20, "1,3", 3, true, "", true,
                "CLOSED_CASH", 0, 0, null, "NONE", null, null, "LOCAL", "user-1",
                "POSTED", 1, 10, 0, "", ""),
            items = listOf(InvoiceItemDtoV2("line-1", "invoice-1", "GOODS", "line", "", "sku", "unit",
                1, 500, 1000, 1000, "", true, "item-1", 500, 1000, 500, 1000, 500, 500, "KNOWN")),
            dueInstallments = emptyList(), payments = emptyList(), paymentAllocations = emptyList(),
            realizedFxEvents = emptyList(), returnDocuments = emptyList(), returnLines = emptyList(),
            returnPaymentAllocations = emptyList(), explicitTombstones = emptyList(), effectReferences = emptyList(),
            businessContentHash = ZERO,
        )
        return draft.copy(businessContentHash = SyncContractV2Codec.financialBusinessHash(draft))
    }

    private fun validMovement() = InventoryMovementDtoV2(
        "movement-1", "item-1", "", "", "PURCHASE", 2, 150, "", "", "PURCHASE",
        "source-1", null, 1, "write-1", "org-1", "command-1", "idem-1", null, null,
        "1", 10, null, null, null, null, "device-1", 2, 10,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val ZERO = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val FINANCIAL_GOLDEN_SHA256 = "8869bb431ede4f7455eb83e0d101863022bcf220f49bc1ed6cdbbb024961f2b7"
    }
}
