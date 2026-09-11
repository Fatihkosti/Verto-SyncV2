package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.feature.shipment.domain.model.LogisticsLocation
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.AssigneeDirectoryPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

data class LogisticsHeaderLocationCommand(
    val countryName: String,
    val city: String,
)

data class SaveLogisticsShipmentHeaderCommand(
    val shipmentId: String,
    val origin: LogisticsHeaderLocationCommand,
    val destination: LogisticsHeaderLocationCommand,
    val employeeId: String,
    val savedAt: Long,
)

class SaveLogisticsShipmentHeaderUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val assignees: AssigneeDirectoryPort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: SaveLogisticsShipmentHeaderCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.savedAt >= 0L) { "savedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment draft not found")
        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT) {
            "Shipment header can only be edited while DRAFT"
        }

        val activeAssignments = aggregate.assignments.filter { it.endedAt == null }
        require(activeAssignments.size <= 1) { "Multiple active assignments found" }
        val activeAssignment = activeAssignments.singleOrNull()

        val employeeId = command.employeeId.trim()
        val employee = if (employeeId.isBlank()) {
            null
        } else {
            assignees.getEmployee(organizationId, employeeId)
                ?: error("Assigned employee was not found in this organization")
        }
        require(employee?.active != false) { "Assigned employee is inactive" }

        val assignment = employee?.let { record ->
            if (activeAssignment?.employeeId == record.employeeId) {
                activeAssignment.copy(employeeNameSnapshot = record.employeeName)
            } else {
                LogisticsAssignment(
                    id = identities.newId(),
                    shipmentId = aggregate.shipment.id,
                    employeeId = record.employeeId,
                    employeeNameSnapshot = record.employeeName,
                    assignedAt = command.savedAt,
                )
            }
        }
        if (assignment == null) {
            require(activeAssignment == null) { "A draft employee assignment cannot be cleared implicitly" }
        }

        val updated = aggregate.shipment.withDefinitionHeader(
            command = command,
            assignee = employee?.let { LogisticsAssigneeSnapshot(it.employeeId, it.employeeName) },
        )
        store.saveShipmentHeader(updated, assignment)
        return updated
    }
}

private fun LogisticsShipment.withDefinitionHeader(
    command: SaveLogisticsShipmentHeaderCommand,
    assignee: LogisticsAssigneeSnapshot?,
): LogisticsShipment {
    val originCountry = LogisticsCountryNormalizer.displayName(command.origin.countryName)
    val originCity = command.origin.city.trim().replace(Regex("\\s+"), " ")
    val destinationCountry = LogisticsCountryNormalizer.displayName(command.destination.countryName)
    val destinationCity = command.destination.city.trim().replace(Regex("\\s+"), " ")
    return copy(
        // Country is report metadata only after v235; legacy display values remain city-only.
        sourceLocation = originCity,
        destinationLocation = destinationCity,
        originLocationDetails = LogisticsLocation(
            LogisticsCountryNormalizer.key(originCountry), originCountry, originCity, originCity,
        ),
        destinationLocationDetails = LogisticsLocation(
            LogisticsCountryNormalizer.key(destinationCountry), destinationCountry, destinationCity, destinationCity,
        ),
        assignee = assignee,
    )
}
