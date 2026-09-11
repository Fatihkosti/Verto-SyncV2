package com.verto.app.feature.integration.optimal.data.activityevent

import com.verto.app.data.local.dao.OptimalMaintenanceReadDao
import com.verto.app.feature.integration.optimal.application.activityevent.MaintenanceActivityEventSource
import com.verto.app.feature.integration.optimal.application.activityevent.MaintenanceActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMaintenanceActivityEventSource @Inject constructor(
    private val dao: OptimalMaintenanceReadDao,
) : MaintenanceActivityEventSource {
    override fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<MaintenanceActivityRecord>> = dao.observeActivityRecords(
        organizationId = organizationId,
        sinceEpochMillis = sinceEpochMillis,
        limit = limit,
    ).map { rows ->
        rows.map { row ->
            MaintenanceActivityRecord(
                organizationId = row.organizationId,
                recordId = row.recordId,
                companyName = row.companyName.orEmpty(),
                vehicleName = row.vehicleNameSnapshot,
                vehicleType = row.vehicleTypeSnapshot,
                plateNumber = row.plateNumberSnapshot,
                driverOrDelegate = row.driverOrDelegate,
                notes = row.maintenanceNotes,
                createdAtEpochMillis = row.createdAt,
            )
        }
    }
}
