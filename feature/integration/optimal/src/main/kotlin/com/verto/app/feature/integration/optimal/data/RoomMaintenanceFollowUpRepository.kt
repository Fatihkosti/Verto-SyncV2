package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OperationalMaintenanceFollowUpRow
import com.verto.app.data.local.dao.OptimalMaintenanceFollowUpDao
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpEntity
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpStatus as LocalStatus
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUpStatus
import com.verto.app.feature.integration.optimal.domain.model.OperationalMaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.repository.MaintenanceFollowUpRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMaintenanceFollowUpRepository @Inject constructor(
    private val dao: OptimalMaintenanceFollowUpDao,
) : MaintenanceFollowUpRepository {
    override suspend fun start(
        organizationId: String,
        recordId: String,
        startedAt: Long,
        expectedAt: Long?,
    ): MaintenanceFollowUp = dao.start(
        OptimalMaintenanceFollowUpEntity(
            organizationId = organizationId.trim(),
            recordId = recordId.trim(),
            status = LocalStatus.IN_PROGRESS,
            startedAt = startedAt,
            expectedAt = expectedAt,
            updatedAt = startedAt,
        ),
    ).toDomain()

    override suspend fun updateStatus(
        organizationId: String,
        recordId: String,
        status: MaintenanceFollowUpStatus,
        updatedAt: Long,
    ): MaintenanceFollowUp? = dao.updateStatus(
        organizationId = organizationId.trim(),
        recordId = recordId.trim(),
        status = status.toLocal(),
        updatedAt = updatedAt,
    )?.toDomain()

    override suspend fun updateExpectedAt(
        organizationId: String,
        recordId: String,
        expectedAt: Long?,
        updatedAt: Long,
    ): MaintenanceFollowUp? = dao.updateExpectedAt(
        organizationId = organizationId.trim(),
        recordId = recordId.trim(),
        expectedAt = expectedAt,
        updatedAt = updatedAt,
    )?.toDomain()

    override fun observeOperational(
        organizationId: String,
        nowMillis: Long,
    ): Flow<List<OperationalMaintenanceFollowUp>> = dao.observeOperational(
        organizationId = organizationId.trim(),
        nowMillis = nowMillis,
    ).map { rows -> rows.map(OperationalMaintenanceFollowUpRow::toDomain) }
}

private fun OptimalMaintenanceFollowUpEntity.toDomain() = MaintenanceFollowUp(
    organizationId = organizationId,
    recordId = recordId,
    status = MaintenanceFollowUpStatus.valueOf(status.name),
    startedAt = startedAt,
    expectedAt = expectedAt,
    updatedAt = updatedAt,
)

private fun OperationalMaintenanceFollowUpRow.toDomain() = OperationalMaintenanceFollowUp(
    followUp = MaintenanceFollowUp(
        organizationId = organizationId,
        recordId = recordId,
        status = MaintenanceFollowUpStatus.valueOf(status.name),
        startedAt = startedAt,
        expectedAt = expectedAt,
        updatedAt = updatedAt,
    ),
    invoiceId = invoiceId,
    overdue = overdue,
    vehicleName = vehicleName,
    vehicleType = vehicleType,
    plateNumber = plateNumber,
)

private fun MaintenanceFollowUpStatus.toLocal(): LocalStatus = LocalStatus.valueOf(name)
