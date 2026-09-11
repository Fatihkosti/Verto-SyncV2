package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.PrepareLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.RecordLogisticsCostCommand
import com.verto.app.feature.shipment.domain.model.SaveShipmentRouteCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsPlanningOrchestratorTest {
    @Test
    fun `planning sequence keeps stable request ids across retry`() = runBlocking {
        val operations = FakePlanningOperations()
        val orchestrator = LogisticsPlanningOrchestrator(operations)
        val command = command(markReady = true)

        val first = orchestrator(command)
        val firstIds = operations.requestIds.toList()
        operations.requestIds.clear()
        val second = orchestrator(command)

        assertEquals(LogisticsShipmentState.READY, first.state)
        assertEquals(first, second)
        assertEquals(firstIds, operations.requestIds)
        assertEquals(listOf("prepare", "route", "aggregate", "ready"), operations.calls.take(4))
        assertTrue(firstIds.all(String::isNotBlank))
    }

    @Test
    fun `route failure stops later planning side effects`() = runBlocking {
        val operations = FakePlanningOperations(failOnRoute = true)
        val failure = runCatching { LogisticsPlanningOrchestrator(operations)(command(markReady = true)) }

        assertTrue(failure.isFailure)
        assertEquals(listOf("prepare", "route"), operations.calls)
        assertTrue("aggregate" !in operations.calls)
        assertTrue("ready" !in operations.calls)
    }

    private fun command(markReady: Boolean) = LogisticsPlanningWorkflowCommand(
        shipmentId = "shipment",
        core = LogisticsPlanningWorkflowCore(
            assignee = LogisticsAssigneeSnapshot("employee", "Employee"),
            sources = emptyList(),
            lines = emptyList(),
        ),
        schedule = LogisticsPlanningWorkflowSchedule(null, null, null),
        route = LogisticsPlanningWorkflowRoute(emptyList(), emptyList()),
        artifacts = LogisticsPlanningWorkflowArtifacts(emptyList(), emptyList()),
        submission = LogisticsPlanningWorkflowSubmission(markReady, "planning-request"),
    )

    private class FakePlanningOperations(
        private val failOnRoute: Boolean = false,
    ) : LogisticsPlanningOperations {
        val calls = mutableListOf<String>()
        val requestIds = mutableListOf<String>()
        private val draft = LogisticsShipment(
            id = "shipment",
            organizationId = "org",
            shipmentNumber = "S-322",
            sourceLocation = "Cairo",
            destinationLocation = "Abu Hamed",
            createdAt = 1L,
        )
        private val ready = draft.copy(state = LogisticsShipmentState.READY)

        override suspend fun organizationId(): String = "org"

        override suspend fun aggregate(organizationId: String, shipmentId: String): LogisticsShipmentAggregate {
            calls += "aggregate"
            return LogisticsShipmentAggregate(shipment = draft)
        }

        override suspend fun prepare(organizationId: String, command: PrepareLogisticsShipmentCommand) {
            calls += "prepare"
            requestIds += command.requestId
        }

        override suspend fun saveRoute(organizationId: String, command: SaveShipmentRouteCommand) {
            calls += "route"
            requestIds += command.requestId
            if (failOnRoute) error("route failed")
        }

        override suspend fun recordCost(organizationId: String, command: RecordLogisticsCostCommand) {
            calls += "cost"
            requestIds += command.requestId
        }

        override suspend fun saveDocument(command: SaveLogisticsDocumentUseCase.Command) {
            calls += "document"
            requestIds += command.requestId
        }

        override suspend fun markReady(organizationId: String, shipmentId: String, requestId: String): LogisticsShipment {
            calls += "ready"
            requestIds += requestId
            return ready
        }

        override suspend fun removeDraft(organizationId: String, shipmentId: String, privateUri: String) {
            calls += "remove-draft"
        }
    }
}
