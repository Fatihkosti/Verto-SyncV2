package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecord
import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalRemoteContract
import javax.inject.Inject

/**
 * Decides whether a local maintenance record can produce an Optimal remote payload.
 * Manual vehicle text never enters the remote payload and can never imply vehicle creation.
 */
class OptimalMaintenancePayloadMapper @Inject constructor(
    private val contractGate: OptimalBackendContractGate,
) {
    internal fun map(
        command: PersistInvoiceIntegrationCommand,
        persisted: MaintenanceRecord,
    ): MaintenanceOutboxMapping {
        val organizationId = command.organizationId.trim()
        val invoiceId = command.invoiceId.trim()
        val clientId = command.clientId.trim()
        require(organizationId.isNotEmpty()) { "organizationId is required" }
        require(invoiceId.isNotEmpty()) { "invoiceId is required" }
        require(clientId.isNotEmpty()) { "clientId is required" }
        require(persisted.organizationId == organizationId) {
            "maintenance record crosses the invoice organization"
        }
        require(persisted.invoiceId == invoiceId) {
            "maintenance record does not belong to the invoice"
        }

        val officialVehicle = persisted.vehicleReference
            ?: return MaintenanceOutboxMapping(
                disposition = MaintenancePayloadDisposition.LOCAL_ONLY_MANUAL_VEHICLE,
                remotePayload = null,
            )

        require(officialVehicle.clientId == clientId) {
            "official vehicle does not belong to the invoice company"
        }
        if (!contractGate.allows(OptimalRemoteContract.UPSERT_MAINTENANCE)) {
            return MaintenanceOutboxMapping(
                disposition = MaintenancePayloadDisposition.LOCAL_ONLY_CONTRACT_UNAVAILABLE,
                remotePayload = null,
            )
        }

        return MaintenanceOutboxMapping(
            disposition = MaintenancePayloadDisposition.REMOTE_READY,
            remotePayload = OptimalMaintenanceRemotePayload(
                identity = OptimalMaintenanceRemoteIdentity(
                    organizationId, invoiceId, clientId, persisted.recordId, officialVehicle.vehicleId,
                ),
                snapshot = OptimalMaintenanceRemoteSnapshot(
                    persisted.vehicleSnapshot.name, persisted.vehicleSnapshot.vehicleType,
                    persisted.vehicleSnapshot.plateNumber, persisted.driverOrDelegate, persisted.notes,
                ),
            ),
        )
    }
}

internal enum class MaintenancePayloadDisposition {
    REMOTE_READY,
    LOCAL_ONLY_MANUAL_VEHICLE,
    LOCAL_ONLY_CONTRACT_UNAVAILABLE,
}

internal data class MaintenanceOutboxMapping(
    val disposition: MaintenancePayloadDisposition,
    val remotePayload: OptimalMaintenanceRemotePayload?,
)

/** Existing official vehicle reference only; no create-vehicle shape exists in this payload. */
internal data class OptimalMaintenanceRemoteIdentity(
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val recordId: String,
    val vehicleId: String,
)

internal data class OptimalMaintenanceRemoteSnapshot(
    val vehicleName: String,
    val vehicleType: String,
    val plateNumber: String,
    val driverOrDelegateValue: String,
    val notesValue: String,
)

internal data class OptimalMaintenanceRemotePayload(
    val identity: OptimalMaintenanceRemoteIdentity,
    val snapshot: OptimalMaintenanceRemoteSnapshot,
) {
    val organizationId: String get() = identity.organizationId
    val invoiceId: String get() = identity.invoiceId
    val clientId: String get() = identity.clientId
    val recordId: String get() = identity.recordId
    val vehicleId: String get() = identity.vehicleId
    val vehicleNameSnapshot: String get() = snapshot.vehicleName
    val vehicleTypeSnapshot: String get() = snapshot.vehicleType
    val plateNumberSnapshot: String get() = snapshot.plateNumber
    val driverOrDelegate: String get() = snapshot.driverOrDelegateValue
    val notes: String get() = snapshot.notesValue
}
