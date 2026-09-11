package com.verto.app.feature.shipment.domain.validation

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LogisticsV230PlanningTest {
    @Test
    fun `unified planning route accepts no carrier and derives road`() {
        val shipment = shipment()
        val milestones = directMilestones()
        val legs = listOf(leg("l1", 0, "m0", "m1", LogisticsLegTransportMode.ROAD))

        LogisticsValidation.validatePlanningRoute(shipment, milestones, legs)

        assertEquals(LogisticsTransportMode.ROAD, LogisticsValidation.deriveTransportMode(legs))
    }

    @Test
    fun `mixed planning defers leg transport mode until execution`() {
        val shipment = shipment()
        val milestones = listOf(
            milestone("m0", 0, LogisticsMilestoneType.ORIGIN, "Sudan • Port Sudan"),
            milestone("mx", 1, LogisticsMilestoneType.TRANSIT, "Halfa"),
            milestone("m1", 2, LogisticsMilestoneType.DESTINATION, "Sudan • Abu Hamed"),
        )
        val legs = listOf(
            leg("l1", 0, "m0", "mx", LogisticsLegTransportMode.UNSPECIFIED),
            leg("l2", 1, "mx", "m1", LogisticsLegTransportMode.UNSPECIFIED),
        )

        LogisticsValidation.validatePlanningRoute(shipment, milestones, legs)

        assertEquals(LogisticsTransportMode.MULTIMODAL, LogisticsValidation.deriveTransportMode(legs))
    }

    @Test
    fun `customs is optional but requires expected duration when selected`() {
        LogisticsValidation.validatePlanningRoute(
            shipment(), directMilestones(), listOf(leg("l1", 0, "m0", "m1", LogisticsLegTransportMode.ROAD)),
        )

        val invalidCustoms = listOf(
            milestone("m0", 0, LogisticsMilestoneType.ORIGIN, "Sudan • Port Sudan"),
            milestone("mc", 1, LogisticsMilestoneType.CUSTOMS, "Halfa"),
            milestone("m1", 2, LogisticsMilestoneType.DESTINATION, "Sudan • Abu Hamed"),
        )
        val legs = listOf(
            leg("l1", 0, "m0", "mc", LogisticsLegTransportMode.ROAD),
            leg("l2", 1, "mc", "m1", LogisticsLegTransportMode.ROAD),
        )
        check(runCatching { LogisticsValidation.validatePlanningRoute(shipment(), invalidCustoms, legs) }.isFailure)

        val validCustoms = invalidCustoms.map { if (it.id == "mc") it.copy(expectedStayDays = 2) else it }
        LogisticsValidation.validatePlanningRoute(shipment(), validCustoms, legs)
    }

    @Test
    fun `planning rejects carrier cargo and missing transit duration`() {
        val shipment = shipment()
        val milestones = directMilestones()
        val operationalLeg = leg("l1", 0, "m0", "m1", LogisticsLegTransportMode.ROAD)
            .copy(carrierPartnerId = "carrier", packageCount = 4)
        check(runCatching { LogisticsValidation.validatePlanningRoute(shipment, milestones, listOf(operationalLeg)) }.isFailure)

        val noDuration = operationalLeg.copy(
            carrierPartnerId = "",
            packageCount = null,
            expectedTransitDays = null,
            expectedTransitMinutes = null,
        )
        check(runCatching { LogisticsValidation.validatePlanningRoute(shipment, milestones, listOf(noDuration)) }.isFailure)
    }

    private fun shipment() = LogisticsShipment(
        id = "shipment", organizationId = "org", shipmentNumber = "0001",
        sourceLocation = "Sudan • Port Sudan", destinationLocation = "Sudan • Abu Hamed", createdAt = 1L,
    )

    private fun directMilestones() = listOf(
        milestone("m0", 0, LogisticsMilestoneType.ORIGIN, "Sudan • Port Sudan"),
        milestone("m1", 1, LogisticsMilestoneType.DESTINATION, "Sudan • Abu Hamed"),
    )

    private fun milestone(id: String, order: Int, type: LogisticsMilestoneType, location: String) =
        LogisticsMilestone(id = id, shipmentId = "shipment", type = type, order = order, location = location)

    private fun leg(id: String, sequence: Int, from: String, to: String, mode: LogisticsLegTransportMode) =
        LogisticsShipmentLeg(
            id = id, organizationId = "org", shipmentId = "shipment", sequence = sequence,
            fromMilestoneId = from, toMilestoneId = to, mode = mode, carrierPartnerId = "", expectedTransitDays = 2,
        )
}
