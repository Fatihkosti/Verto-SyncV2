package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV230SelectionTest {
    @Test
    fun `selecting invoice links the entire invoice and removing supplier unlinks all its invoices`() {
        val optionA = invoice("a", "supplier", listOf("a1", "a2"))
        val optionB = invoice("b", "supplier", listOf("b1"))
        val selected = LogisticsPlanningDraft(shipmentId = "shipment")
            .addWholeInvoice(optionA)
            .addWholeInvoice(optionB)

        assertEquals(setOf("a", "b"), selected.sources.map { it.invoiceId }.toSet())
        assertEquals(setOf("a1", "a2", "b1"), selected.lines.map { it.sourceInvoiceItemId }.toSet())

        val removed = selected.removeSupplier("supplier")
        assertTrue(removed.sources.isEmpty())
        assertTrue(removed.lines.isEmpty())
    }

    @Test
    fun `route template round trip keeps stations customs timing and mixed planning`() {
        val draft = LogisticsPlanningDraft(
            organizationId = "org", shipmentId = "shipment",
            sourceLocation = logisticsPlanningPlace("مصر", "القاهرة"),
            destinationLocation = logisticsPlanningPlace("السودان", "أبوحمد"),
        )
        val milestones = listOf(
            LogisticsMilestone("m0", "shipment", LogisticsMilestoneType.ORIGIN, 0, draft.sourceLocation),
            LogisticsMilestone("mc", "shipment", LogisticsMilestoneType.CUSTOMS, 1, "حلفا", expectedStayDays = 2),
            LogisticsMilestone("m1", "shipment", LogisticsMilestoneType.DESTINATION, 2, draft.destinationLocation),
        )
        val legs = listOf(
            LogisticsShipmentLeg("l0", "org", "shipment", 0, "m0", "mc", LogisticsLegTransportMode.UNSPECIFIED, "", expectedTransitDays = 3),
            LogisticsShipmentLeg("l1", "org", "shipment", 1, "mc", "m1", LogisticsLegTransportMode.UNSPECIFIED, "", expectedTransitDays = 1),
        )
        val workspace = LogisticsRouteWorkspaceSnapshot(
            shipmentId = "shipment", templateName = "مصر إلى أبوحمد",
            routeTransportPlanKind = LogisticsRouteTransportPlanKind.MIXED, milestones = milestones, legs = legs,
        )

        val template = workspace.toRouteTemplate(draft)
        val restored = LogisticsRouteWorkspaceSnapshot(shipmentId = "shipment").applyTemplate(draft, template)

        assertEquals(LogisticsRouteTransportPlanKind.MIXED, template.transportPlanKind)
        assertEquals(1, template.customsStopOrder)
        assertEquals(2 * 24 * 60, template.expectedCustomsMinutes)
        assertEquals(listOf(3, 1), restored.legs.map { it.expectedTransitDays })
        assertTrue(restored.legs.all { it.mode == LogisticsLegTransportMode.UNSPECIFIED && it.carrierPartnerId.isBlank() })
        assertEquals(LogisticsMilestoneType.CUSTOMS, restored.milestones[1].type)
    }

    @Test
    fun `shipping contacts autocomplete matches representative name and excludes unrelated roles`() {
        val contacts = listOf(
            LogisticsPartner("carrier", "org", "خوجلي", LogisticsPartnerRole.CARRIER, representativeName = "هشام", representativePhone = "091234"),
            LogisticsPartner("other", "org", "جهة أخرى", LogisticsPartnerRole.OTHER, representativeName = "هشام"),
        )

        val matches = contacts.matchingShippingContacts("هش")

        assertEquals(listOf("carrier"), matches.map { it.id })
    }

    private fun invoice(invoiceId: String, supplierId: String, lineIds: List<String>): LogisticsPurchaseInvoiceOptionUi {
        val source = LogisticsShipmentSource(
            id = "source-$invoiceId", shipmentId = "shipment", invoiceId = invoiceId, supplierId = supplierId,
            supplierNameSnapshot = "Supplier", invoiceNumberSnapshot = invoiceId,
        )
        return LogisticsPurchaseInvoiceOptionUi(
            source = source,
            lines = lineIds.map { lineId ->
                LogisticsShipmentLine(
                    id = "line-$lineId", shipmentId = "shipment", sourceInvoiceId = invoiceId,
                    sourceInvoiceItemId = lineId, inventoryItemId = "item-$lineId", itemNameSnapshot = lineId,
                    expectedQuantity = 2, basePurchaseUnitPrice = BigDecimal.TEN,
                )
            },
        )
    }
}
