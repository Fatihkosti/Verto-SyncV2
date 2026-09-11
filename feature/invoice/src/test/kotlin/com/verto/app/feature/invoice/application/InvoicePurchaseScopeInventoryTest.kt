package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceDraftItem
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoicePostingIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceSaleStockMutation
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceLinePostingStockPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoicePurchaseScopeInventoryTest {
    @Test
    fun local_purchase_posts_stock_even_without_shipment_link() = runTest {
        val stock = RecordingStockPort()
        val writer = InvoiceInventoryWriter(stock)
        val result = writer.writeForCreate(request(PurchaseScope.LOCAL))
        assertEquals(1, stock.addCalls)
        assertEquals(1, result.newItemsCreated)
    }

    @Test
    fun international_purchase_never_posts_stock_even_without_shipment_link() = runTest {
        val stock = RecordingStockPort()
        val writer = InvoiceInventoryWriter(stock)
        val result = writer.writeForCreate(request(PurchaseScope.INTERNATIONAL))
        assertEquals(0, stock.addCalls)
        assertEquals(0, result.newItemsCreated)
        assertEquals(0, result.itemsUpdated)
    }

    @Test
    fun local_purchase_posts_every_line_with_distinct_line_identity() = runTest {
        val stock = RecordingStockPort()
        val writer = InvoiceInventoryWriter(stock)
        val command = SaveInvoiceCommand(
            existingInvoiceId = null,
            clientId = "supplier",
            items = listOf(
                InvoiceDraftItem(name = "Part A", quantity = "2", sellPrice = "0", buyPrice = "10"),
                InvoiceDraftItem(name = "Part B", quantity = "3", sellPrice = "0", buyPrice = "20"),
            ),
            paymentMode = InvoicePaymentMode.CASH,
            dueDate = 0,
            notes = "",
            isSale = false,
            purchaseScope = PurchaseScope.LOCAL,
        )
        val request = InvoiceInventoryWriteRequest(
            command = command,
            invoiceId = "invoice-two-lines",
            allowNegativeStock = false,
            context = InvoiceInventoryContext(emptyList(), emptyMap()),
            validated = InvoiceSaveValidator().validate(command),
            actorId = "employee",
            actorName = "Employee",
        )
        val lines = listOf(
            InvoiceLine(id = "invoice-line-a", invoiceId = "invoice-two-lines", itemName = "Part A", quantity = 2),
            InvoiceLine(id = "invoice-line-b", invoiceId = "invoice-two-lines", itemName = "Part B", quantity = 3),
        )

        writer.writeForCreate(request, lines)

        assertEquals(2, stock.purchaseCommands.size)
        assertNotEquals(stock.purchaseCommands[0].writeId, stock.purchaseCommands[1].writeId)
        assertEquals("invoice-line-a", stock.purchaseCommands[0].sourceLineId)
        assertEquals("invoice-line-b", stock.purchaseCommands[1].sourceLineId)
        assertTrue(stock.purchaseCommands.all { it.postingGroupId == "invoice-post:invoice-two-lines" })
    }

    @Test
    fun local_sale_posts_every_line_with_distinct_line_identity() = runTest {
        val stock = RecordingStockPort()
        stock.saveItem(InvoiceStockItem(id = "item-a", name = "Part A", quantity = 10))
        stock.saveItem(InvoiceStockItem(id = "item-b", name = "Part B", quantity = 10))
        val writer = InvoiceInventoryWriter(stock)
        val command = SaveInvoiceCommand(
            existingInvoiceId = null,
            clientId = "client",
            items = listOf(
                InvoiceDraftItem(inventoryItemId = "item-a", name = "Part A", quantity = "2", sellPrice = "30", buyPrice = "10"),
                InvoiceDraftItem(inventoryItemId = "item-b", name = "Part B", quantity = "3", sellPrice = "40", buyPrice = "20"),
            ),
            paymentMode = InvoicePaymentMode.CASH,
            dueDate = 0,
            notes = "",
            isSale = true,
        )
        val request = InvoiceInventoryWriteRequest(
            command = command,
            invoiceId = "sale-two-lines",
            allowNegativeStock = false,
            context = writer.loadContext(),
            validated = InvoiceSaveValidator().validate(command),
            actorId = "employee",
            actorName = "Employee",
        )
        val lines = listOf(
            InvoiceLine(id = "sale-line-a", invoiceId = "sale-two-lines", itemName = "Part A", quantity = 2),
            InvoiceLine(id = "sale-line-b", invoiceId = "sale-two-lines", itemName = "Part B", quantity = 3),
        )

        writer.writeForCreate(request, lines)

        assertEquals(2, stock.saleIdentities.size)
        assertNotEquals(stock.saleIdentities[0].writeId, stock.saleIdentities[1].writeId)
        assertEquals("sale-line-a", stock.saleIdentities[0].sourceLineId)
        assertEquals("sale-line-b", stock.saleIdentities[1].sourceLineId)
        assertTrue(stock.saleIdentities.all { it.postingGroupId == "invoice-post:sale-two-lines" })
    }

    private fun request(scope: PurchaseScope): InvoiceInventoryWriteRequest {
        val command = SaveInvoiceCommand(
            existingInvoiceId = null,
            clientId = "supplier",
            items = listOf(InvoiceDraftItem(name = "Part", quantity = "2", sellPrice = "0", buyPrice = "10")),
            paymentMode = InvoicePaymentMode.CASH,
            dueDate = 0,
            notes = "",
            isSale = false,
            shipmentId = null,
            purchaseScope = scope,
        )
        return InvoiceInventoryWriteRequest(
            command = command,
            invoiceId = "invoice",
            allowNegativeStock = false,
            context = InvoiceInventoryContext(emptyList(), emptyMap()),
            validated = InvoiceSaveValidator().validate(command),
            actorId = "employee",
            actorName = "Employee",
        )
    }

    private class RecordingStockPort : InvoiceStockPort, InvoiceLinePostingStockPort {
        var addCalls = 0
        val purchaseCommands = mutableListOf<InvoicePurchaseStockCommand>()
        val saleIdentities = mutableListOf<InvoicePostingIdentity>()
        private val items = mutableMapOf<String, InvoiceStockItem>()
        override suspend fun getAllItems() = items.values.toList()
        override suspend fun getItem(itemId: String) = items[itemId]
        override suspend fun saveItem(item: InvoiceStockItem) { items[item.id] = item }
        override suspend fun deductStock(itemId: String, quantity: Int, invoiceId: String, clientId: String, unitPrice: Double, allowNegativeStock: Boolean, sourceWriteId: String) = Result.success(Unit)
        override suspend fun deductPostingLine(mutation: InvoiceSaleStockMutation, identity: InvoicePostingIdentity): Result<Unit> {
            saleIdentities += identity
            val before = requireNotNull(items[mutation.itemId])
            items[mutation.itemId] = before.copy(quantity = before.quantity - mutation.quantity)
            return Result.success(Unit)
        }
        override suspend fun addStock(itemId: String, quantity: Int, invoiceId: String, supplierId: String, unitPrice: Double, sourceWriteId: String): Result<Unit> { addCalls++; return Result.success(Unit) }
        override suspend fun receivePurchaseAtLatestPrice(command: InvoicePurchaseStockCommand): com.verto.app.feature.invoice.domain.model.InvoiceInventoryRevaluationRecord? {
            addCalls++
            purchaseCommands += command
            val before = requireNotNull(items[command.itemId])
            items[command.itemId] = before.copy(quantity = before.quantity + command.quantity)
            return null
        }
        override suspend fun deleteMovements(invoiceId: String) = Unit
        override suspend fun reverseMovements(invoiceId: String, sourceWriteId: String) = Unit
    }
}
