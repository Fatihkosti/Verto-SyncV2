package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPlanChangeScope
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevision
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionChange
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsV234Contract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV234ContractCharacterizationTest {
    @Test
    fun `customs is an event after a real station and never after destination`() {
        val stations = listOf(
            station("origin", 0, LogisticsMilestoneType.ORIGIN),
            station("halfa", 1, LogisticsMilestoneType.TRANSIT),
            station("destination", 2, LogisticsMilestoneType.DESTINATION),
        )
        LogisticsV234Contract.requireCustomsPlan(customs(afterStationId = "halfa"), stations)

        assertFails { LogisticsV234Contract.requireCustomsPlan(customs(afterStationId = "destination"), stations) }
        assertFails { LogisticsV234Contract.requireCustomsPlan(customs(afterStationId = "missing"), stations) }
    }

    @Test
    fun `legacy customs milestone is readable but not a v234 station`() {
        @Suppress("DEPRECATION")
        val legacy = station("legacy-customs", 1, LogisticsMilestoneType.CUSTOMS)
        assertFalse(LogisticsV234Contract.isStation(legacy))
    }

    @Test
    fun `started or completed leg history cannot be edited or removed`() {
        val started = leg("leg-1", LogisticsLegStatus.IN_TRANSIT, actualDepartureAt = 100)
        val future = leg("leg-2", LogisticsLegStatus.PLANNED)
        val aggregate = LogisticsShipmentAggregate(shipment(), legs = listOf(started, future))

        assertFails {
            LogisticsV234PlanningPolicy.requireFutureOnlyEdit(
                aggregate,
                listOf(started.copy(expectedTransitMinutes = 999), future),
            )
        }
        assertFails { LogisticsV234PlanningPolicy.requireFutureOnlyEdit(aggregate, listOf(future)) }
        LogisticsV234PlanningPolicy.requireFutureOnlyEdit(
            aggregate,
            listOf(started, future.copy(expectedTransitMinutes = 180)),
        )
    }

    @Test
    fun `revision one is approval and later edits are sequential auditable changes`() {
        val approval = revision(1, LogisticsPlanRevisionKind.INITIAL_APPROVAL, reason = "", changes = emptyList())
        LogisticsV234Contract.requireRevision(approval, currentRevision = 0)
        val approvedShipment = shipment().copy(
            state = LogisticsShipmentState.READY,
            currentPlanRevision = 1,
            planApprovedAt = 10,
        )
        LogisticsV234Contract.requireRevisionShipmentTransition(shipment(), approvedShipment, approval)
        assertFails {
            LogisticsV234Contract.requireRevisionShipmentTransition(
                shipment(),
                approvedShipment.copy(state = LogisticsShipmentState.DRAFT),
                approval,
            )
        }

        val edit = revision(
            2,
            LogisticsPlanRevisionKind.FUTURE_EDIT,
            reason = "Route changed before movement",
            changes = listOf(
                LogisticsPlanRevisionChange(
                    id = "change-1",
                    scope = LogisticsPlanChangeScope.LEG,
                    scopeId = "leg-2",
                    fieldKey = "expectedTransitMinutes",
                    previousValue = "120",
                    newValue = "180",
                ),
            ),
        )
        LogisticsV234Contract.requireRevision(edit, currentRevision = 1)
        assertFails { LogisticsV234Contract.requireRevision(edit.copy(revisionNumber = 3), currentRevision = 1) }
        assertFails { LogisticsV234Contract.requireRevision(edit.copy(reason = ""), currentRevision = 1) }
    }

    @Test
    fun `cancelling started shipment keeps invoice reservation while unstarted cancellation releases it`() {
        val unstarted = shipment(state = LogisticsShipmentState.CANCELLED, startedAt = null)
        val started = shipment(state = LogisticsShipmentState.CANCELLED, startedAt = 100)

        assertTrue(LogisticsV234CancellationPolicy.releasesInvoiceReservation(unstarted))
        assertFalse(LogisticsV234CancellationPolicy.releasesInvoiceReservation(started))
        assertFalse(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(started))
        assertTrue(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(shipment()))
        assertTrue(LogisticsV234CancellationPolicy.preservesExecutionHistory(started))
    }

    @Test
    fun `illegal lifecycle transition remains rejected`() {
        assertFalse(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.DRAFT, LogisticsShipmentState.CLOSED))
        assertFails { LogisticsLifecyclePolicy.requireTransition(LogisticsShipmentState.DRAFT, LogisticsShipmentState.CLOSED) }
    }

    private fun shipment(
        state: LogisticsShipmentState = LogisticsShipmentState.DRAFT,
        startedAt: Long? = null,
    ) = LogisticsShipment(
        id = "shipment",
        organizationId = "org",
        shipmentNumber = "S-1",
        sourceLocation = "Cairo",
        destinationLocation = "Abu Hamed",
        state = state,
        createdAt = 1,
        startedAt = startedAt,
    )

    private fun station(id: String, order: Int, type: LogisticsMilestoneType) = LogisticsMilestone(
        id = id,
        shipmentId = "shipment",
        type = type,
        order = order,
        location = id,
    )

    private fun leg(
        id: String,
        status: LogisticsLegStatus,
        actualDepartureAt: Long? = null,
    ) = LogisticsShipmentLeg(
        id = id,
        organizationId = "org",
        shipmentId = "shipment",
        sequence = if (id == "leg-1") 0 else 1,
        fromMilestoneId = if (id == "leg-1") "origin" else "halfa",
        toMilestoneId = if (id == "leg-1") "halfa" else "destination",
        mode = LogisticsLegTransportMode.ROAD,
        carrierPartnerId = "",
        status = status,
        expectedTransitDays = 1,
        expectedTransitMinutes = 120,
        actualDepartureAt = actualDepartureAt,
    )

    private fun customs(afterStationId: String) = LogisticsCustomsPlan(
        id = "customs-plan",
        organizationId = "org",
        shipmentId = "shipment",
        checkpointName = "Halfa Customs",
        afterStationId = afterStationId,
        expectedDurationMinutes = 720,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun revision(
        number: Int,
        kind: LogisticsPlanRevisionKind,
        reason: String,
        changes: List<LogisticsPlanRevisionChange>,
    ) = LogisticsPlanRevision(
        id = "revision-$number",
        organizationId = "org",
        shipmentId = "shipment",
        revisionNumber = number,
        kind = kind,
        reason = reason,
        recordedAt = number.toLong(),
        requestId = "request-$number",
        changes = changes,
    )

    private inline fun assertFails(block: () -> Unit) {
        val result = runCatching(block)
        assertTrue("Expected validation to reject operation", result.isFailure)
    }
}
