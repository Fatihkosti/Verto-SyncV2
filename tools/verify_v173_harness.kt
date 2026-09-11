package com.verto.app.feature.shipment.verification

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.*
import java.math.BigDecimal
import kotlin.coroutines.*
import kotlinx.coroutines.flow.Flow

private class Ids : LogisticsIdentityPort {
    private var n = 0
    override fun newId(): String = "id-${++n}"
}

private class Clock(private val value: Long = 1000L) : LogisticsClockPort { override fun now(): Long = value }

private class FakeStore(
    var aggregate: LogisticsShipmentAggregate,
    val partnerMap: MutableMap<String, LogisticsPartner>,
) : LogisticsShipmentStorePort {
    val requests = mutableSetOf<String>()
    var inventoryPosted = false
    override fun observeShipments(organizationId: String): Flow<List<LogisticsShipment>> = object : Flow<List<LogisticsShipment>> {}
    override suspend fun getShipment(organizationId: String, shipmentId: String) = aggregate.takeIf { it.shipment.organizationId == organizationId && it.shipment.id == shipmentId }
    override suspend fun shipmentNumberExists(organizationId: String, shipmentNumber: String) = false
    override suspend fun isRequestProcessed(organizationId: String, requestId: String) = requestId in requests
    override suspend fun getPartner(organizationId: String, partnerId: String) = partnerMap[partnerId]?.takeIf { it.organizationId == organizationId }
    override suspend fun hasInventoryPosting(organizationId: String, shipmentId: String) = inventoryPosted
    override suspend fun createShipment(shipment: LogisticsShipment, event: LogisticsEvent) = error("unused")
    override suspend fun savePlanning(shipment: LogisticsShipment, sources: List<LogisticsShipmentSource>, lines: List<LogisticsShipmentLine>, milestones: List<LogisticsMilestone>, assignment: LogisticsAssignment, event: LogisticsEvent) = error("unused")
    override suspend fun saveShipmentState(shipment: LogisticsShipment, event: LogisticsEvent) {
        aggregate = aggregate.copy(shipment = shipment); requests += event.requestId
    }
    override suspend fun changeAssignment(shipment: LogisticsShipment, endedAssignmentId: String?, assignment: LogisticsAssignment, event: LogisticsEvent) = error("unused")
    override suspend fun saveMilestone(shipment: LogisticsShipment, milestone: LogisticsMilestone, event: LogisticsEvent) = error("unused")
    override suspend fun saveRoute(shipment: LogisticsShipment, milestones: List<LogisticsMilestone>, legs: List<LogisticsShipmentLeg>, event: LogisticsEvent) {
        aggregate = aggregate.copy(shipment = shipment, milestones = milestones, legs = legs); requests += event.requestId
    }
    override suspend fun saveCustodyHandoff(handoff: LogisticsCustodyHandoff, event: LogisticsEvent) {
        aggregate = aggregate.copy(custodyHandoffs = aggregate.custodyHandoffs + handoff); requests += event.requestId
    }
    override suspend fun saveOperationalUpdate(shipment: LogisticsShipment, milestones: List<LogisticsMilestone>, legs: List<LogisticsShipmentLeg>, event: LogisticsEvent) {
        val milestoneMap = milestones.associateBy { it.id }
        val legMap = legs.associateBy { it.id }
        aggregate = aggregate.copy(
            shipment = shipment,
            milestones = aggregate.milestones.map { milestoneMap[it.id] ?: it },
            legs = aggregate.legs.map { legMap[it.id] ?: it },
        )
        requests += event.requestId
    }
    override suspend fun appendEvent(event: LogisticsEvent) { requests += event.requestId }
    override suspend fun upsertPartner(partner: LogisticsPartner) { partnerMap[partner.id] = partner }
    override suspend fun linkPartner(organizationId: String, link: LogisticsShipmentPartnerLink) = error("unused")
    override suspend fun saveDocument(document: LogisticsDocument, event: LogisticsEvent) = error("unused")
    override suspend fun deleteDocument(organizationId: String, shipmentId: String, documentId: String) = false
    override suspend fun saveCost(cost: LogisticsCost, event: LogisticsEvent) = error("unused")
    override suspend fun getCosts(organizationId: String, shipmentId: String) = emptyList<LogisticsCost>()
    override suspend fun saveReceivingBatch(batch: LogisticsReceivingBatch, updatedShipment: LogisticsShipment, event: LogisticsEvent) = error("unused")
    override suspend fun saveCostAllocations(organizationId: String, shipmentId: String, allocations: List<LogisticsCostAllocation>, event: LogisticsEvent) = error("unused")
}

private fun baseAggregate(state: LogisticsShipmentState = LogisticsShipmentState.READY): LogisticsShipmentAggregate {
    val shipment = LogisticsShipment(
        id = "s1", organizationId = "org", shipmentNumber = "1", sourceLocation = "A", destinationLocation = "B",
        state = state, createdAt = 1, transportMode = LogisticsTransportMode.ROAD,
        assignee = LogisticsAssigneeSnapshot("e1", "Employee"), startedAt = if (state == LogisticsShipmentState.IN_TRANSIT) 100L else null,
    )
    val sources = listOf(
        LogisticsShipmentSource("srcA", "s1", "invA", "supA", "Supplier A", "A-1"),
        LogisticsShipmentSource("srcB", "s1", "invB", "supB", "Supplier B", "B-1"),
    )
    val lines = listOf(
        LogisticsShipmentLine("lineA", "s1", "invA", "itemA", "stockA", "A", 1, BigDecimal.ONE),
        LogisticsShipmentLine("lineB", "s1", "invB", "itemB", "stockB", "B", 1, BigDecimal.ONE),
    )
    val milestones = listOf(
        LogisticsMilestone("m0", "s1", LogisticsMilestoneType.ORIGIN, 0, "A"),
        LogisticsMilestone("m1", "s1", LogisticsMilestoneType.DESTINATION, 1, "B", plannedArrivalAt = 500L),
    )
    val leg = LogisticsShipmentLeg(
        id = "leg0", organizationId = "org", shipmentId = "s1", sequence = 0,
        fromMilestoneId = "m0", toMilestoneId = "m1", mode = LogisticsLegTransportMode.ROAD,
        carrierPartnerId = "c1", status = if (state == LogisticsShipmentState.IN_TRANSIT) LogisticsLegStatus.IN_TRANSIT else LogisticsLegStatus.PLANNED,
        plannedDepartureAt = 100L, plannedArrivalAt = 500L,
        actualDepartureAt = if (state == LogisticsShipmentState.IN_TRANSIT) 100L else null,
    )
    return LogisticsShipmentAggregate(shipment = shipment, sources = sources, lines = lines, milestones = milestones, legs = listOf(leg))
}

private fun partners() = mutableMapOf(
    "c1" to LogisticsPartner("c1", "org", "Carrier 1", LogisticsPartnerRole.CARRIER, "0911"),
    "c2" to LogisticsPartner("c2", "org", "Carrier 2", LogisticsPartnerRole.FREIGHT_FORWARDER, "0922"),
)

private fun handoff(source: LogisticsShipmentSource, to: String, receivedAt: Long, requestId: String) = LogisticsCustodyHandoff(
    id = "h-$requestId", organizationId = "org", shipmentId = "s1", sourceId = source.id,
    fromHolderType = LogisticsCustodyHolderType.SUPPLIER, fromHolderId = source.supplierId,
    fromHolderNameSnapshot = source.supplierNameSnapshot, toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER,
    toHolderId = to, toHolderNameSnapshot = "Carrier", transferredAt = receivedAt, receivedAt = receivedAt,
    requestId = requestId,
)

private fun assertFails(label: String, block: () -> Unit) {
    var failed = false
    try { block() } catch (_: IllegalArgumentException) { failed = true }
      catch (_: IllegalStateException) { failed = true }
    check(failed) { "$label should fail" }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result = value }
    })
    return result!!.getOrThrow()
}

fun main() {
    val ids = Ids()
    var passed = 0
    fun pass(name: String) { passed++; println("PASS $passed: $name") }

    run {
        val a = baseAggregate().let { it.copy(custodyHandoffs = listOf(handoff(it.sources[0], "c1", 10, "ha"))) }
        val store = FakeStore(a, partners())
        assertFails("source B still supplier") { runSuspend { StartLogisticsShipmentUseCase(store, ids)("org", StartLogisticsShipmentCommand("s1", 100, "start1")) } }
        pass("source A handed off, source B supplier -> Start rejected")
    }
    run {
        val base = baseAggregate()
        val a = base.copy(custodyHandoffs = listOf(handoff(base.sources[0], "c1", 10, "ha"), handoff(base.sources[1], "c1", 11, "hb")))
        val store = FakeStore(a, partners())
        val started = runSuspend { StartLogisticsShipmentUseCase(store, ids)("org", StartLogisticsShipmentCommand("s1", 100, "start2")) }
        check(started.state == LogisticsShipmentState.IN_TRANSIT && store.aggregate.legs.single().status == LogisticsLegStatus.IN_TRANSIT)
        pass("all sources at first carrier -> Start passes")
    }
    run {
        val store = FakeStore(baseAggregate(), partners())
        assertFails("wrong from") {
            runSuspend { RecordCustodyHandoffUseCase(store, ids)("org", RecordCustodyHandoffCommand(
                shipmentId = "s1", sourceId = "srcA", fromHolderType = LogisticsCustodyHolderType.SUPPLIER,
                fromHolderId = "wrong", fromHolderNameSnapshot = "Supplier A", toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER,
                toHolderId = "c1", toHolderNameSnapshot = "Carrier 1", transferredAt = 10, receivedAt = 10, requestId = "wrong-from",
            )) }
        }
        pass("handoff wrong from-holder -> rejected")
    }
    run {
        val store = FakeStore(baseAggregate(), partners())
        val uc = RecordCustodyHandoffUseCase(store, ids)
        val cmd = RecordCustodyHandoffCommand(
            shipmentId = "s1", sourceId = "srcA", fromHolderType = LogisticsCustodyHolderType.SUPPLIER,
            fromHolderId = "supA", fromHolderNameSnapshot = "Supplier A", toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER,
            toHolderId = "c1", toHolderNameSnapshot = "Carrier 1", transferredAt = 10, receivedAt = 10, requestId = "dup",
        )
        val first = runSuspend { uc("org", cmd) }; val second = runSuspend { uc("org", cmd) }
        check(first.id == second.id && store.aggregate.custodyHandoffs.count { it.requestId == "dup" } == 1)
        pass("duplicate handoff request id -> idempotent")
    }
    run {
        val store = FakeStore(baseAggregate(LogisticsShipmentState.IN_TRANSIT), partners())
        val updated = runSuspend { UpdateShipmentEtaUseCase(store, ids)("org", UpdateShipmentEtaCommand("s1", "leg0", 700, 200, "eta-ok")) }
        check(updated.plannedArrivalAt == 700L && store.aggregate.milestones.single { it.id == "m1" }.plannedArrivalAt == 700L)
        pass("future ETA edit -> pass")
    }
    run {
        val base = baseAggregate(LogisticsShipmentState.IN_TRANSIT)
        val arrivedLeg = base.legs.single().copy(status = LogisticsLegStatus.ARRIVED, actualArrivalAt = 450)
        val store = FakeStore(base.copy(legs = listOf(arrivedLeg)), partners())
        assertFails("arrived ETA") { runSuspend { UpdateShipmentEtaUseCase(store, ids)("org", UpdateShipmentEtaCommand("s1", "leg0", 700, 500, "eta-bad")) } }
        pass("actual arrived leg ETA overwrite -> rejected")
    }
    run {
        val store = FakeStore(baseAggregate(LogisticsShipmentState.IN_TRANSIT), partners())
        val cancelled = runSuspend { CancelLogisticsShipmentUseCase(store, ids)("org", CancelLogisticsShipmentCommand("s1", 300, "cancel-ok", "carrier failure")) }
        check(cancelled.state == LogisticsShipmentState.CANCELLED)
        pass("cancel IN_TRANSIT without stock -> pass")
    }
    run {
        val store = FakeStore(baseAggregate(LogisticsShipmentState.IN_TRANSIT), partners()).also { it.inventoryPosted = true }
        assertFails("cancel with stock") { runSuspend { CancelLogisticsShipmentUseCase(store, ids)("org", CancelLogisticsShipmentCommand("s1", 300, "cancel-bad", "reason")) } }
        pass("cancel after inventory posting -> rejected")
    }
    run {
        val base = baseAggregate(LogisticsShipmentState.DRAFT)
        val milestones = listOf(
            base.milestones[0],
            LogisticsMilestone("mx", "s1", LogisticsMilestoneType.TRANSIT, 1, "Port"),
            base.milestones[1].copy(order = 2),
        )
        val legs = listOf(
            base.legs[0].copy(toMilestoneId = "mx", status = LogisticsLegStatus.PLANNED, actualDepartureAt = null),
            LogisticsShipmentLeg("leg1", "org", "s1", 1, "mx", "m1", LogisticsLegTransportMode.SEA, "c2"),
        )
        val store = FakeStore(base, partners())
        val updated = runSuspend { SaveShipmentRouteUseCase(store, ids)("org", SaveShipmentRouteCommand("s1", milestones, legs, 10, "route-mm")) }
        check(updated.transportMode == LogisticsTransportMode.MULTIMODAL)
        pass("multimodal route -> shipment mode derived MULTIMODAL")
    }
    run {
        val base = baseAggregate(LogisticsShipmentState.IN_TRANSIT)
        val sourceSpecific = handoff(base.sources[0], "c1", 10, "h1")
        val shipmentWide = LogisticsCustodyHandoff(
            id = "h2", organizationId = "org", shipmentId = "s1", sourceId = null,
            fromHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, fromHolderId = "c1", fromHolderNameSnapshot = "Carrier 1",
            toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, toHolderId = "c2", toHolderNameSnapshot = "Carrier 2",
            transferredAt = 20, receivedAt = 20, requestId = "h2",
        )
        val agg = base.copy(custodyHandoffs = listOf(sourceSpecific, shipmentWide))
        check(LogisticsCustodyResolver.currentForSource(agg, agg.sources[0]).holderId == "c2")
        pass("shipment-wide handoff overrides older source-specific custody")
    }

    check(passed == 10)
    run {
        val base = baseAggregate(LogisticsShipmentState.IN_TRANSIT)
        val agg = base.copy(custodyHandoffs = listOf(
            handoff(base.sources[0], "c1", 10, "status-a"),
            handoff(base.sources[1], "c1", 11, "status-b"),
        ))
        val store = FakeStore(agg, partners())
        val status = runSuspend { ResolveLogisticsOperationalStatusUseCase(store, Clock(650L))("org", "s1") }
        check(status.currentLocation == "A → B")
        check(status.currentCustodianName == "Carrier")
        check(status.currentCustodianPhone == "0911")
        check(status.nextMilestoneName == "B")
        check(status.nextCarrierName == "Carrier 1")
        check(status.expectedArrivalAt == 500L && status.delayMillis == 150L)
        check(status.activeLegId == "leg0")
        check(LogisticsOperationalAction.CALL in status.availableActions)
        check(LogisticsOperationalAction.RECORD_ARRIVAL in status.availableActions)
        check(LogisticsOperationalAction.CANCEL_SHIPMENT in status.availableActions)
        println("OPERATIONAL_STATUS=PASS")
    }

    println("V173_HARNESS=PASS $passed/10")
}
