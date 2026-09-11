package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CompleteLogisticsCustomsCommand
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyReceiptCounts
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.StartLogisticsCustomsCommand
import com.verto.app.feature.shipment.domain.policy.FridayOffLogisticsCalendarPolicy
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV240StationCustomsExecutionTest {
    @Test
    fun `planned customs event runs after designated real station and transfers custody only on confirmed receipt`() = runBlocking {
        val harness = StoreHarness(customsReadyAggregate(), broker())
        val useCase = StartLogisticsCustomsUseCase(harness.store, identities(), clock(900L))
        val invalid = StartLogisticsCustomsCommand(
            shipmentId = "shipment", milestoneId = "halfa", brokerPartnerId = "broker",
            receivedAt = 1_200L, requestId = "customs-invalid",
            receipt = LogisticsCustodyReceiptCounts(10, 9, 0, 0, discrepancyNote = ""),
        )

        assertTrue(runCatching { useCase("org", invalid) }.isFailure)
        assertEquals("carrier", LogisticsCustodyResolver.currentForAllSources(harness.aggregate).values.single().holderId)
        assertEquals(0, harness.customsSaveCount)

        val handoff = useCase(
            "org",
            invalid.copy(requestId = "customs-start", receipt = invalid.receipt.copy(discrepancyNote = "كرتونة ناقصة عند الاستلام")),
        )
        assertEquals("halfa", handoff.milestoneId)
        assertEquals("broker", LogisticsCustodyResolver.currentForAllSources(harness.aggregate).values.single().holderId)
        assertEquals(LogisticsShipmentState.CUSTOMS, harness.aggregate.shipment.state)
        assertEquals(LogisticsMilestoneType.TRANSIT, harness.aggregate.milestones.single { it.id == "halfa" }.type)
    }

    @Test
    fun `designated station cannot load onward before customs completes then execution returns to station`() = runBlocking {
        val harness = StoreHarness(customsReadyAggregate(), broker())
        val readyStatus = ResolveLogisticsOperationalStatusUseCase(harness.store, clock(1_100L))("org", "shipment")
        assertTrue(LogisticsOperationalAction.START_CUSTOMS in readyStatus.availableActions)

        StartLogisticsCustomsUseCase(harness.store, identities(), clock(900L))(
            "org",
            StartLogisticsCustomsCommand(
                "shipment", "halfa", "broker", 1_200L, "customs-start",
                LogisticsCustodyReceiptCounts(10, 10, 0, 0),
            ),
        )
        val activeHost = harness.aggregate.milestones.single { it.id == "halfa" }
        assertTrue(runCatching { LogisticsV240ExecutionPolicy.requireCustomsCompletedBeforeLoading(harness.aggregate, activeHost) }.isFailure)

        CompleteLogisticsCustomsUseCase(harness.store, identities(), clock(2_100L))(
            "org", CompleteLogisticsCustomsCommand("shipment", "halfa", 2_000L, "customs-complete"),
        )
        val completedHost = harness.aggregate.milestones.single { it.id == "halfa" }
        LogisticsV240ExecutionPolicy.requireCustomsCompletedBeforeLoading(harness.aggregate, completedHost)
        assertEquals(LogisticsShipmentState.AT_STATION, harness.aggregate.shipment.state)
        assertEquals(2_000L, completedHost.customsCompletedAt)
        val afterCustoms = ResolveLogisticsOperationalStatusUseCase(harness.store, clock(2_100L))("org", "shipment")
        assertTrue(LogisticsOperationalAction.HANDOFF in afterCustoms.availableActions)
    }

    @Test
    fun `customs delay excludes friday from working elapsed time`() {
        val zone = ZoneId.of("Africa/Khartoum")
        val thursdayNoon = LocalDateTime.of(2026, 8, 20, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val saturdayNoon = LocalDateTime.of(2026, 8, 22, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val elapsed = LogisticsV240ExecutionPolicy.workingElapsedMillis(
            thursdayNoon, saturdayNoon, zone, FridayOffLogisticsCalendarPolicy,
        )
        assertEquals(24L * 60L * 60L * 1_000L, elapsed)
    }

    private fun customsReadyAggregate(): LogisticsShipmentAggregate {
        val source = LogisticsShipmentSource("source", "shipment", "invoice", "supplier", "Supplier", "INV-1")
        val carrierHandoff = LogisticsCustodyHandoff(
            id = "carrier-handoff", organizationId = "org", shipmentId = "shipment", sourceId = null,
            fromHolderType = LogisticsCustodyHolderType.SUPPLIER, fromHolderId = "supplier", fromHolderNameSnapshot = "Supplier",
            toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, toHolderId = "carrier", toHolderNameSnapshot = "Carrier",
            transferredAt = 500L, receivedAt = 500L, requestId = "carrier-receipt",
            handoverPackageCount = 10, receivedPackageCount = 10,
        )
        return LogisticsShipmentAggregate(
            shipment = LogisticsShipment(
                "shipment", "org", "0001", "Cairo", "Abu Hamed",
                state = LogisticsShipmentState.AT_STATION, createdAt = 0L, startedAt = 100L,
                eventTimezoneId = "Africa/Khartoum",
            ),
            sources = listOf(source),
            milestones = listOf(
                LogisticsMilestone("origin", "shipment", LogisticsMilestoneType.ORIGIN, 0, "Cairo", arrivedAt = 100L, departedAt = 200L),
                LogisticsMilestone(
                    "halfa", "shipment", LogisticsMilestoneType.TRANSIT, 1, "Wadi Halfa",
                    arrivedAt = 1_000L, handlingStatus = LogisticsMilestoneHandlingStatus.UNLOADED, unloadedAt = 1_050L,
                ),
                LogisticsMilestone("destination", "shipment", LogisticsMilestoneType.DESTINATION, 2, "Abu Hamed"),
            ),
            legs = listOf(
                LogisticsShipmentLeg(
                    id = "leg-next", organizationId = "org", shipmentId = "shipment", sequence = 1,
                    fromMilestoneId = "halfa", toMilestoneId = "destination", mode = LogisticsLegTransportMode.ROAD,
                    carrierPartnerId = "next-carrier",
                ),
            ),
            custodyHandoffs = listOf(carrierHandoff),
            customsPlan = LogisticsCustomsPlan(
                "customs-plan", "org", "shipment", "Wadi Halfa Customs", "halfa", 24 * 60, 0L, 0L,
            ),
        )
    }

    private fun broker() = LogisticsPartner("broker", "org", "Broker", LogisticsPartnerRole.CUSTOMS_BROKER, phone = "249912345678")
    private fun identities() = object : LogisticsIdentityPort {
        private var next = 0
        override fun newId(): String = "v240-${++next}"
    }
    private fun clock(value: Long) = object : LogisticsClockPort { override fun now(): Long = value }

    private class StoreHarness(initial: LogisticsShipmentAggregate, private val broker: LogisticsPartner) {
        var aggregate = initial
        var customsSaveCount = 0
        private val processed = mutableSetOf<String>()
        val store: LogisticsShipmentStorePort = Proxy.newProxyInstance(
            LogisticsShipmentStorePort::class.java.classLoader,
            arrayOf(LogisticsShipmentStorePort::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getShipment" -> aggregate
                "isRequestProcessed" -> args?.get(1) in processed
                "getPartner" -> broker.takeIf { it.id == args?.get(1) }
                "hasInventoryPosting" -> false
                "saveCustomsTransition" -> {
                    val shipment = args?.get(0) as LogisticsShipment
                    val milestone = args[1] as LogisticsMilestone
                    val handoff = args[2] as LogisticsCustodyHandoff?
                    val event = args[3] as LogisticsEvent
                    aggregate = aggregate.copy(
                        shipment = shipment,
                        milestones = aggregate.milestones.map { if (it.id == milestone.id) milestone else it },
                        custodyHandoffs = aggregate.custodyHandoffs + listOfNotNull(handoff),
                    )
                    processed += event.requestId
                    customsSaveCount += 1
                    Unit
                }
                "toString" -> "LogisticsV240StoreHarness"
                else -> error("Unexpected store method: ${method.name}")
            }
        } as LogisticsShipmentStorePort
    }
}
