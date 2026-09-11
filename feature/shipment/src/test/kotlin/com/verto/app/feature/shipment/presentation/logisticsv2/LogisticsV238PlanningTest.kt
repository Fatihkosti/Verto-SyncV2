package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsDurationUnit
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV238PlanningTest {
    @Test
    fun `customs requires checkpoint station before destination and positive duration`() {
        val initial = workspace()
        assertFalse(initial.v238CustomsValidation().isValid)

        val valid = initial.copy(
            customsCheckpointName = "حلفا",
            customsAfterStationId = "origin",
            customsExpectedDurationMinutes = 2 * 24 * 60,
        )
        assertTrue(valid.v238CustomsValidation().isValid)

        val afterDestination = valid.copy(customsAfterStationId = "destination")
        assertFalse(afterDestination.v238CustomsValidation().isValid)
    }

    @Test
    fun `customs stays separate from route milestones`() {
        val valid = workspace().copy(
            customsCheckpointName = "حلفا",
            customsAfterStationId = "origin",
            customsExpectedDurationMinutes = 24 * 60,
        )

        assertTrue(valid.milestones.none { it.type == LogisticsMilestoneType.CUSTOMS })
        assertEquals(2, valid.milestones.size)
        assertTrue(valid.v238CustomsValidation().isValid)
    }

    @Test
    fun `customs duration supports days and hours`() {
        val days = workspace().withV238CustomsDuration(3, LogisticsDurationUnit.DAYS)
        assertEquals(3 * 24 * 60, days.customsExpectedDurationMinutes)
        assertEquals(LogisticsDurationUnit.DAYS, days.v238CustomsDurationUnit())
        assertEquals(3, days.v238CustomsDurationValue())

        val hours = workspace().withV238CustomsDuration(7, LogisticsDurationUnit.HOURS)
        assertEquals(7 * 60, hours.customsExpectedDurationMinutes)
        assertEquals(LogisticsDurationUnit.HOURS, hours.v238CustomsDurationUnit())
        assertEquals(7, hours.v238CustomsDurationValue())
    }

    @Test
    fun `pending customs document blocks customs completion`() {
        val pending = LogisticsPendingDocumentDraft(
            draftId = "doc",
            sourceUri = "content://doc",
            displayName = "customs.pdf",
            mimeType = "application/pdf",
            milestoneId = V238_CUSTOMS_DOCUMENT_TARGET,
            isStaging = true,
        )
        val value = workspace().copy(
            customsCheckpointName = "حلفا",
            customsAfterStationId = "origin",
            customsExpectedDurationMinutes = 24 * 60,
            pendingDocuments = listOf(pending),
        )

        assertFalse(value.v238CustomsValidation().isValid)
        assertTrue(value.v238CustomsValidation().documentError != null)
    }

    private fun workspace(): LogisticsRouteWorkspaceSnapshot {
        val milestones = listOf(
            LogisticsMilestone("origin", "shipment", LogisticsMilestoneType.ORIGIN, 0, "Port Sudan"),
            LogisticsMilestone("destination", "shipment", LogisticsMilestoneType.DESTINATION, 1, "Abu Hamed"),
        )
        return LogisticsRouteWorkspaceSnapshot(
            shipmentId = "shipment",
            tripTypeSelected = true,
            routeTransportPlanKind = LogisticsRouteTransportPlanKind.UNIFIED,
            unifiedTransportMode = LogisticsLegTransportMode.ROAD,
            unifiedTransportModeSelected = true,
            milestones = milestones,
            legs = listOf(
                LogisticsShipmentLeg(
                    id = "leg", organizationId = "org", shipmentId = "shipment", sequence = 0,
                    fromMilestoneId = "origin", toMilestoneId = "destination",
                    mode = LogisticsLegTransportMode.ROAD, carrierPartnerId = "",
                    expectedTransitMinutes = 24 * 60,
                ),
            ),
        )
    }
}
