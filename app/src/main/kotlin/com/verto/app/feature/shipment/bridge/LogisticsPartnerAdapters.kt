package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsAssignmentEntity
import com.verto.app.data.local.entity.LogisticsPartnerEntity
import com.verto.app.data.local.entity.LogisticsShipmentPartnerLinkEntity
import com.verto.app.data.remote.AuthRepository
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentPartnerLink
import com.verto.app.feature.shipment.domain.port.AssigneeDirectoryPort
import com.verto.app.feature.shipment.domain.port.LogisticsAssigneeRecord
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject
import kotlinx.coroutines.flow.map

internal class LogisticsPartnerStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()

    suspend fun getPartner(organizationId: String, partnerId: String): LogisticsPartner? =
        dao.getPartner(organizationId, partnerId)?.toDomainV2()

    suspend fun listPartners(organizationId: String): List<LogisticsPartner> =
        dao.getPartners(organizationId).map(LogisticsPartnerEntity::toDomainV2)

    suspend fun upsertPartner(partner: LogisticsPartner) {
        LogisticsValidation.validatePartner(partner)
        database.withTransaction {
            val entity = partner.toEntityV2()
            val existing = dao.getPartner(partner.organizationId, partner.id)
            if (existing == null) dao.insertPartner(entity)
            else require(dao.updatePartner(entity) == 1) { "Partner update failed" }
        }
    }

    suspend fun linkPartner(organizationId: String, link: LogisticsShipmentPartnerLink) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        database.withTransaction {
            require(dao.getShipment(organizationId, link.shipmentId) != null) { "Shipment not found" }
            val partner = dao.getPartner(organizationId, link.partnerId) ?: error("Partner not found")
            require(partner.role == link.role.name) { "Partner role does not match shipment link role" }
            dao.insertPartnerLink(link.toEntityV2(organizationId))
        }
    }
}

class ExistingLogisticsAssigneeDirectoryAdapter @Inject constructor(
    private val authRepository: AuthRepository,
) : AssigneeDirectoryPort {
    override suspend fun getEmployee(
        organizationId: String,
        employeeId: String,
    ): LogisticsAssigneeRecord? {
        val profile = authRepository.getMyProfile()
        if (profile != null && profile.organizationId == organizationId && profile.id == employeeId) {
            return LogisticsAssigneeRecord(
                employeeId = profile.id,
                employeeName = profile.name,
                active = profile.isActive,
            )
        }
        return authRepository.getOrgEmployees().getOrNull()
            ?.firstOrNull { it.userId == employeeId }
            ?.let { employee ->
                LogisticsAssigneeRecord(
                    employeeId = employee.userId,
                    employeeName = employee.name,
                    active = employee.isActive,
                )
            }
    }
}

internal fun LogisticsPartnerEntity.toDomainV2() = LogisticsPartner(
    id = id,
    organizationId = organizationId,
    name = name,
    role = LogisticsPartnerRole.valueOf(role),
    phone = phone,
    representativeName = representativeName,
    representativePhone = representativePhone,
    notes = notes,
)

internal fun LogisticsPartner.toEntityV2() = LogisticsPartnerEntity(
    organizationId = organizationId,
    id = id,
    name = name,
    role = role.name,
    phone = phone,
    representativeName = representativeName,
    representativePhone = representativePhone,
    notes = notes,
)

internal fun LogisticsShipmentPartnerLink.toEntityV2(organizationId: String) = LogisticsShipmentPartnerLinkEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    partnerId = partnerId,
    role = role.name,
)

internal fun LogisticsShipmentPartnerLinkEntity.toDomainV2() = LogisticsShipmentPartnerLink(
    id = id,
    shipmentId = shipmentId,
    partnerId = partnerId,
    role = LogisticsPartnerRole.valueOf(role),
)

internal fun LogisticsAssignmentEntity.toDomainV2(): LogisticsAssignment = LogisticsAssignment(
    id = id,
    shipmentId = shipmentId,
    employeeId = employeeId,
    employeeNameSnapshot = employeeNameSnapshot,
    assignedAt = assignedAt,
    endedAt = endedAt,
)

internal fun LogisticsAssignment.toEntityV2(organizationId: String): LogisticsAssignmentEntity =
    LogisticsAssignmentEntity(
        organizationId = organizationId,
        id = id,
        shipmentId = shipmentId,
        employeeId = employeeId,
        employeeNameSnapshot = employeeNameSnapshot,
        assignedAt = assignedAt,
        endedAt = endedAt,
    )
