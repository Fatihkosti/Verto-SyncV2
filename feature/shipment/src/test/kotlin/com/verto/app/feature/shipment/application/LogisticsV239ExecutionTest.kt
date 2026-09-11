package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsMovementExecutionFacts
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.PrepareLogisticsMovementCommand
import com.verto.app.feature.shipment.domain.model.StartLogisticsMovementCommand
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV239ExecutionTest {
    @Test
    fun `preparation records actual facts without starting movement and retry is idempotent`() = runBlocking {
        val harness = StoreHarness(readyAggregate(), carrier())
        val useCase = PrepareLogisticsMovementUseCase(harness.store, identities(), clock(500L))
        val command = PrepareLogisticsMovementCommand(
            shipmentId = "shipment",
            legId = "leg-0",
            facts = LogisticsMovementExecutionFacts(
                carrierPartnerId = "carrier", representativeName = "Ali", representativePhone = "+249912345678",
                packageCount = 12, weightKg = BigDecimal("850.5"),
            ),
            preparedAt = 100L,
            requestId = "prepare-1",
        )

        val prepared = useCase("org", command)
        assertEquals(LogisticsShipmentState.WAITING_DEPARTURE, harness.aggregate.shipment.state)
        assertNull(harness.aggregate.shipment.startedAt)
        assertEquals("carrier", prepared.carrierPartnerId)
        assertEquals(12, prepared.packageCount)
        assertEquals(BigDecimal("10"), prepared.plannedWeightKg)
        assertEquals(LogisticsEventType.MOVEMENT_PREPARED, harness.lastEvent?.type)
        assertEquals(100L, harness.lastEvent?.occurredAt)
        assertEquals(500L, harness.lastEvent?.recordedAt)

        useCase("org", command)
        assertEquals(1, harness.operationalSaveCount)
    }

    @Test
    fun `movement cannot start before custody transfer then starts ETA clock from actual departure`() = runBlocking {
        val harness = StoreHarness(readyAggregate(), carrier())
        PrepareLogisticsMovementUseCase(harness.store, identities(), clock(500L))(
            "org",
            PrepareLogisticsMovementCommand(
                shipmentId = "shipment", legId = "leg-0",
                facts = LogisticsMovementExecutionFacts("carrier", packageCount = 10, weightKg = BigDecimal("500")),
                preparedAt = 100L, requestId = "prepare-1",
            ),
        )
        val start = StartLogisticsMovementUseCase(harness.store, identities(), clock(900L))
        val command = StartLogisticsMovementCommand("shipment", movedAt = 1_000L, requestId = "move-1")
        assertTrue(runCatching { start("org", command) }.isFailure)

        harness.aggregate = harness.aggregate.copy(
            custodyHandoffs = listOf(
                LogisticsCustodyHandoff(
                    id = "handoff", organizationId = "org", shipmentId = "shipment", sourceId = "source",
                    fromHolderType = LogisticsCustodyHolderType.SUPPLIER, fromHolderId = "supplier", fromHolderNameSnapshot = "Supplier",
                    toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, toHolderId = "carrier", toHolderNameSnapshot = "Carrier",
                    transferredAt = 500L, receivedAt = 500L, requestId = "handoff-1",
                ),
            ),
        )
        val moving = start("org", command)
        assertEquals(LogisticsShipmentState.IN_TRANSIT, moving.state)
        assertEquals(1_000L, moving.startedAt)
        val activeLeg = harness.aggregate.legs.single()
        assertEquals(LogisticsLegStatus.IN_TRANSIT, activeLeg.status)
        assertEquals(1_000L + 120L * 60_000L, activeLeg.plannedArrivalAt)
        assertEquals(LogisticsEventType.SHIPMENT_MOVEMENT_STARTED, harness.lastEvent?.type)
        assertEquals(1_000L, harness.lastEvent?.occurredAt)
        assertEquals(900L, harness.lastEvent?.recordedAt)
    }

    @Test
    fun `hour based planned duration drives delay clock`() {
        val base = readyAggregate()
        val active = base.legs.single().copy(
            status = LogisticsLegStatus.IN_TRANSIT,
            carrierPartnerId = "carrier",
            packageCount = 10,
            weightKg = BigDecimal("500"),
            actualDepartureAt = 0L,
            expectedTransitDays = null,
            expectedTransitMinutes = 120,
        )
        val aggregate = base.copy(
            shipment = base.shipment.copy(state = LogisticsShipmentState.IN_TRANSIT, startedAt = 0L),
            legs = listOf(active),
        )
        val harness = StoreHarness(aggregate, carrier())
        val delay = EvaluateLogisticsDelayUseCase(harness.store, clock(0L)).evaluate(
            aggregate = aggregate,
            now = 180L * 60_000L,
            partners = listOf(carrier()),
        )

        assertEquals(120L * 60_000L, delay.timing.expectedMillis)
        assertEquals(60L * 60_000L, delay.timing.delayMillis)
        assertEquals(LogisticsDelayLevel.LATE, delay.level)
    }

    private fun readyAggregate() = LogisticsShipmentAggregate(
        shipment = LogisticsShipment(
            id = "shipment", organizationId = "org", shipmentNumber = "0001",
            sourceLocation = "Port Sudan", destinationLocation = "Abu Hamed",
            state = LogisticsShipmentState.READY, createdAt = 0L,
        ),
        sources = listOf(
            LogisticsShipmentSource(
                id = "source", shipmentId = "shipment", invoiceId = "invoice", supplierId = "supplier",
                supplierNameSnapshot = "Supplier", invoiceNumberSnapshot = "INV-1",
            ),
        ),
        milestones = listOf(
            LogisticsMilestone("origin", "shipment", LogisticsMilestoneType.ORIGIN, 0, "Port Sudan"),
            LogisticsMilestone("destination", "shipment", LogisticsMilestoneType.DESTINATION, 1, "Abu Hamed"),
        ),
        legs = listOf(
            LogisticsShipmentLeg(
                id = "leg-0", organizationId = "org", shipmentId = "shipment", sequence = 0,
                fromMilestoneId = "origin", toMilestoneId = "destination", mode = LogisticsLegTransportMode.ROAD,
                carrierPartnerId = "", expectedTransitMinutes = 120,
                plannedCarrierPartnerId = "planned-carrier", plannedCarrierNameSnapshot = "Planned Carrier",
                plannedPackageCount = 9, plannedWeightKg = BigDecimal("10"),
            ),
        ),
    )

    private fun carrier() = LogisticsPartner("carrier", "org", "Carrier", LogisticsPartnerRole.CARRIER)
    private fun identities() = object : LogisticsIdentityPort {
        private var next = 0
        override fun newId(): String = "id-${++next}"
    }
    private fun clock(value: Long) = object : LogisticsClockPort { override fun now(): Long = value }

    private class StoreHarness(initial: LogisticsShipmentAggregate, private val partner: LogisticsPartner) {
        var aggregate = initial
        var operationalSaveCount = 0
        var lastEvent: LogisticsEvent? = null
        private val processed = mutableSetOf<String>()

        val store: LogisticsShipmentStorePort = Proxy.newProxyInstance(
            LogisticsShipmentStorePort::class.java.classLoader,
            arrayOf(LogisticsShipmentStorePort::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getShipment" -> aggregate
                "isRequestProcessed" -> args?.get(1) in processed
                "getPartner" -> partner.takeIf { it.id == args?.get(1) }
                "saveOperationalUpdate" -> {
                    val shipment = args?.get(0) as LogisticsShipment
                    @Suppress("UNCHECKED_CAST")
                    val milestones = args[1] as List<LogisticsMilestone>
                    @Suppress("UNCHECKED_CAST")
                    val legs = args[2] as List<LogisticsShipmentLeg>
                    val event = args[3] as LogisticsEvent
                    aggregate = aggregate.copy(
                        shipment = shipment,
                        milestones = aggregate.milestones.map { current -> milestones.firstOrNull { it.id == current.id } ?: current },
                        legs = aggregate.legs.map { current -> legs.firstOrNull { it.id == current.id } ?: current },
                    )
                    processed += event.requestId
                    lastEvent = event
                    operationalSaveCount += 1
                    Unit
                }
                "toString" -> "LogisticsV239StoreHarness"
                else -> error("Unexpected store method: ${method.name}")
            }
        } as LogisticsShipmentStorePort
    }
}
