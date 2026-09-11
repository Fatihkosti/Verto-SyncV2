package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV237RoutePlanningTest {
    @Test
    fun `fixed trip requires explicit mode and applies it to every movement`() {
        val draft = draft()
        val initial = workspace(draft)

        val fixed = initial.copy(unifiedTransportModeSelected = false)
            .withV237TripKind(LogisticsRouteTransportPlanKind.UNIFIED)
        assertFalse(fixed.v237TripValidation().isValid)

        val road = fixed.withV237UnifiedMode(LogisticsLegTransportMode.ROAD)
        assertTrue(road.v237TripValidation().isValid)
        assertTrue(road.legs.all { it.mode == LogisticsLegTransportMode.ROAD })
    }

    @Test
    fun `mixed trip requires a concrete mode for every movement`() {
        val draft = draft()
        val mixed = workspace(draft).withV237TripKind(LogisticsRouteTransportPlanKind.MIXED)
        assertFalse(draft.isV237RouteReady(mixed))

        val complete = mixed.copy(
            legs = mixed.legs.mapIndexed { index, leg ->
                validLeg(leg).copy(mode = if (index == 0) LogisticsLegTransportMode.SEA else LogisticsLegTransportMode.ROAD)
            },
        )
        assertTrue(draft.isV237RouteReady(complete))
    }

    @Test
    fun `cargo defaults come from selected purchase invoice totals`() {
        val draft = draft()
        val populated = workspace(draft).withV237CargoDefaults(draft)

        assertEquals(13, populated.legs.first().plannedPackageCount)
        assertEquals(0, BigDecimal("200.00").compareTo(populated.legs.first().plannedWeightKg))
        assertNull(populated.legs.first().packageCount)
        assertNull(populated.legs.first().weightKg)
    }

    @Test
    fun `new movement defaults cargo from previous planned movement before invoice totals`() {
        val draft = draft(withTwoStops = true)
        val base = workspace(draft, withTwoStops = true)
        val first = base.legs.first().copy(
            plannedPackageCount = 9,
            plannedWeightKg = BigDecimal("140"),
        )
        val resetFollowing = base.copy(
            legs = listOf(first) + base.legs.drop(1).map { it.copy(plannedPackageCount = null, plannedWeightKg = null) },
        ).withV237CargoDefaults(draft)

        assertTrue(resetFollowing.legs.drop(1).all { it.plannedPackageCount == 9 })
        assertTrue(resetFollowing.legs.drop(1).all { it.plannedWeightKg?.compareTo(BigDecimal("140")) == 0 })
    }

    @Test
    fun `reorder rebuilds adjacency and forces changed movements to be reviewed again`() {
        val draft = draft(withTwoStops = true)
        val initial = workspace(draft, withTwoStops = true).withV237UnifiedMode(LogisticsLegTransportMode.ROAD)
            .copy(legs = workspace(draft, withTwoStops = true).legs.map(::validLeg))
        val ordered = initial.milestones.sortedBy { it.order }
        val secondStopId = ordered[2].id

        val reordered = initial.withV237ReorderedIntermediate(draft, secondStopId, -1)

        assertEquals(secondStopId, reordered.milestones.sortedBy { it.order }[1].id)
        assertEquals(reordered.milestones.size - 1, reordered.legs.size)
        assertTrue(reordered.legs.any { it.expectedTransitMinutes == null && it.expectedTransitDays == null })
    }

    @Test
    fun `immediate duplicate requires explicit confirmation`() {
        val draft = draft()
        val base = workspace(draft)
        val duplicateId = base.milestones.last().id
        val duplicated = base.copy(
            milestones = base.milestones.map { milestone ->
                if (milestone.id == duplicateId) milestone.copy(location = "Port Sudan", placeName = "Port Sudan") else milestone
            },
        )

        assertEquals(duplicateId, duplicated.v237UnconfirmedImmediateDuplicateId())
        assertNull(duplicated.withV237DuplicateConfirmed(duplicateId).v237UnconfirmedImmediateDuplicateId())
    }

    @Test
    fun `decimal input keeps one separator and remains parseable`() {
        assertEquals("0.125", v237DecimalInput("..12.5"))
        assertEquals("12.34", v237DecimalInput("12.3.4"))
    }

    @Test
    fun `planned local cost keeps base currency at exchange rate one`() {
        val cost = requireNotNull(v237PlannedCost("125.50", "SDG", ""))
        assertEquals(0, BigDecimal.ONE.compareTo(cost.exchangeRate))
        assertEquals(0, BigDecimal("125.50").compareTo(cost.baseCurrencyAmount))
    }

    @Test
    fun `foreign cost remains draftable until positive exchange rate is entered`() {
        val pending = requireNotNull(v237PlannedCost("10", "USD", ""))
        assertEquals(0, BigDecimal.ZERO.compareTo(pending.exchangeRate))
        val leg = validLeg(workspace(draft()).legs.single()).copy(plannedCost = pending)
        val target = workspace(draft()).milestones.last()

        assertFalse(leg.v237Validation(target, LogisticsRouteTransportPlanKind.UNIFIED).isValid)
        assertEquals("أدخل سعر صرف أكبر من صفر", leg.v237Validation(target, LogisticsRouteTransportPlanKind.UNIFIED).exchangeRateError)
    }

    private fun draft(withTwoStops: Boolean = false): LogisticsPlanningDraft {
        val sources = listOf(
            LogisticsShipmentSource(
                id = "s1", shipmentId = "shipment", invoiceId = "i1", supplierId = "a",
                supplierNameSnapshot = "A", invoiceNumberSnapshot = "1",
                plannedPackageCount = 8, plannedWeightKg = BigDecimal("120.25"), expectedReadyAt = 1L,
            ),
            LogisticsShipmentSource(
                id = "s2", shipmentId = "shipment", invoiceId = "i2", supplierId = "b",
                supplierNameSnapshot = "B", invoiceNumberSnapshot = "2",
                plannedPackageCount = 5, plannedWeightKg = BigDecimal("79.75"), expectedReadyAt = 2L,
            ),
        )
        return LogisticsPlanningDraft(
            organizationId = "org",
            shipmentId = "shipment",
            sourceLocation = "Port Sudan",
            destinationLocation = "Abu Hamed",
            sources = sources,
            milestones = if (withTwoStops) milestones(true) else milestones(false),
        )
    }

    private fun workspace(draft: LogisticsPlanningDraft, withTwoStops: Boolean = false): LogisticsRouteWorkspaceSnapshot {
        val milestones = milestones(withTwoStops)
        val legs = milestones.zipWithNext().mapIndexed { index, (from, to) ->
            LogisticsShipmentLeg(
                id = "l$index", organizationId = "org", shipmentId = "shipment", sequence = index,
                fromMilestoneId = from.id, toMilestoneId = to.id,
                mode = LogisticsLegTransportMode.ROAD, carrierPartnerId = "",
            )
        }
        return LogisticsRouteWorkspaceSnapshot(
            shipmentId = "shipment",
            tripTypeSelected = true,
            routeTransportPlanKind = LogisticsRouteTransportPlanKind.UNIFIED,
            unifiedTransportMode = LogisticsLegTransportMode.ROAD,
            unifiedTransportModeSelected = true,
            milestones = milestones,
            legs = legs,
        ).withV237CargoDefaults(draft)
    }

    private fun milestones(withTwoStops: Boolean): List<LogisticsMilestone> {
        val values = mutableListOf(
            LogisticsMilestone("m0", "shipment", LogisticsMilestoneType.ORIGIN, 0, "Port Sudan", placeName = "Port Sudan"),
        )
        if (withTwoStops) {
            values += LogisticsMilestone("m1", "shipment", LogisticsMilestoneType.TRANSIT, 1, "Cairo", placeName = "Cairo")
            values += LogisticsMilestone("m2", "shipment", LogisticsMilestoneType.TRANSIT, 2, "Halfa", placeName = "Halfa")
        }
        values += LogisticsMilestone("m9", "shipment", LogisticsMilestoneType.DESTINATION, values.size, "Abu Hamed", placeName = "Abu Hamed")
        return values
    }

    private fun validLeg(leg: LogisticsShipmentLeg) = leg.copy(
        expectedTransitMinutes = 12 * 60,
        plannedPackageCount = 13,
        plannedWeightKg = BigDecimal("200"),
        carrierPartnerId = "",
        packageCount = null,
        weightKg = null,
    )
}
