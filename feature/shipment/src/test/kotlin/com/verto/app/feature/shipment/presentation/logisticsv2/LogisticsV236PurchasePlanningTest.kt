package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV236PurchasePlanningTest {
    @Test
    fun `invoice requires cartons weight and ready date before purchase step can continue`() {
        val option = invoice("4582", "supplier-a", "المورد الأول")
        val selected = LogisticsPlanningDraft(shipmentId = "shipment").addWholeInvoice(option)

        assertFalse(selected.isPurchaseStepReady(listOf(option)))
        assertEquals("أدخل عدد الكراتين", selected.sources.single().v236Validation().packageCountError)
        assertEquals("أدخل الوزن", selected.sources.single().v236Validation().weightError)
        assertEquals("حدد تاريخ الجاهزية", selected.sources.single().v236Validation().readyDateError)

        val completed = selected.updateInvoicePlanningMetadata(
            invoiceId = option.id,
            packageCount = 12,
            weightKg = BigDecimal("245.5"),
            expectedReadyAt = 1_800_000_000_000L,
        )

        assertTrue(completed.sources.single().v236Validation().isValid)
        assertTrue(completed.isPurchaseStepReady(listOf(option)))
    }

    @Test
    fun `multiple suppliers stay independent and supplier removal affects only its draft invoices`() {
        val first = invoice("100", "supplier-a", "الأول")
        val second = invoice("200", "supplier-b", "الثاني")
        val selected = LogisticsPlanningDraft(shipmentId = "shipment")
            .addWholeInvoice(first)
            .addWholeInvoice(second)

        val remaining = selected.removeSupplier("supplier-a")

        assertEquals(listOf("200"), remaining.sources.map { it.invoiceId })
        assertEquals(listOf("200-item"), remaining.lines.map { it.sourceInvoiceItemId })
    }

    @Test
    fun `v236 search matches invoice metadata but never hidden item names`() {
        val invoice = invoice("4582", "supplier", "المورد", itemName = "فلتر زيت سري")

        assertEquals(listOf("4582"), listOf(invoice).filterV236Invoices("4582").map { it.id })
        assertTrue(listOf(invoice).filterV236Invoices("فلتر زيت").isEmpty())
    }

    @Test
    fun `invoice planning totals sum cartons and decimal weight for station defaults`() {
        val first = invoice("1", "supplier-a", "الأول").source.copy(
            plannedPackageCount = 8,
            plannedWeightKg = BigDecimal("120.25"),
            expectedReadyAt = 1L,
        )
        val second = invoice("2", "supplier-b", "الثاني").source.copy(
            plannedPackageCount = 5,
            plannedWeightKg = BigDecimal("79.75"),
            expectedReadyAt = 2L,
        )
        val draft = LogisticsPlanningDraft(shipmentId = "shipment", sources = listOf(first, second))

        assertEquals(13, draft.v236PlannedPackageTotal)
        assertEquals(0, BigDecimal("200.00").compareTo(draft.v236PlannedWeightTotal))
    }

    private fun invoice(
        invoiceId: String,
        supplierId: String,
        supplierName: String,
        itemName: String = "صنف",
    ): LogisticsPurchaseInvoiceOptionUi {
        val source = LogisticsShipmentSource(
            id = "source-$invoiceId",
            shipmentId = "shipment",
            invoiceId = invoiceId,
            supplierId = supplierId,
            supplierNameSnapshot = supplierName,
            invoiceNumberSnapshot = invoiceId,
        )
        val line = LogisticsShipmentLine(
            id = "line-$invoiceId",
            shipmentId = "shipment",
            sourceInvoiceId = invoiceId,
            sourceInvoiceItemId = "$invoiceId-item",
            inventoryItemId = "inventory-$invoiceId",
            itemNameSnapshot = itemName,
            expectedQuantity = 3,
            basePurchaseUnitPrice = BigDecimal.TEN,
        )
        return LogisticsPurchaseInvoiceOptionUi(source = source, lines = listOf(line))
    }
}
