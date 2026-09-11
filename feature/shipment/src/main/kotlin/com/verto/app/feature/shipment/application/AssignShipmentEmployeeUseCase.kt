package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.AssignLogisticsShipmentEmployeeCommand
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.AssigneeDirectoryPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class AssignShipmentEmployeeUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val assignees: AssigneeDirectoryPort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: AssignLogisticsShipmentEmployeeCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.employeeId.isNotBlank()) { "employeeId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.assignedAt >= 0L) { "assignedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        require(aggregate.shipment.state != LogisticsShipmentState.DRAFT) {
            "Draft assignee is set during shipment preparation"
        }
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED) { "Closed shipment cannot be reassigned" }
        require(aggregate.shipment.state != LogisticsShipmentState.CANCELLED) { "Cancelled shipment cannot be reassigned" }
        val activeAssignment = aggregate.assignments.singleOrNull { it.endedAt == null }
            ?: error("Active shipment assignment not found")

        val employee = assignees.getEmployee(organizationId, command.employeeId.trim())
            ?: error("Assigned employee was not found in this organization")
        require(employee.active) { "Assigned employee is inactive" }

        val snapshot = LogisticsAssigneeSnapshot(employee.employeeId, employee.employeeName)
        val updatedShipment = aggregate.shipment.copy(assignee = snapshot)
        val assignment = LogisticsAssignment(
            id = identities.newId(),
            shipmentId = aggregate.shipment.id,
            employeeId = snapshot.employeeId,
            employeeNameSnapshot = snapshot.employeeName,
            assignedAt = command.assignedAt,
        )
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = aggregate.shipment.id,
            type = LogisticsEventType.ASSIGNEE_CHANGED,
            occurredAt = command.assignedAt,
            employeeId = snapshot.employeeId,
            employeeNameSnapshot = snapshot.employeeName,
            requestId = command.requestId,
        )

        store.changeAssignment(updatedShipment, activeAssignment.id, assignment, event)
        return updatedShipment
    }
}
