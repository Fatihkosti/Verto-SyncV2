package com.verto.app.feature.integration.optimal.application.activityevent

import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventKey
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.ActivityEventSubject
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class MaintenanceActivityRecord(
    val organizationId: String,
    val recordId: String,
    val companyName: String,
    val vehicleName: String,
    val vehicleType: String,
    val plateNumber: String,
    val driverOrDelegate: String,
    val notes: String,
    val createdAtEpochMillis: Long,
)

interface MaintenanceActivityEventSource {
    fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<MaintenanceActivityRecord>>
}

class MaintenanceActivityEventProvider @Inject constructor(
    private val source: MaintenanceActivityEventSource,
    private val sessionReader: SessionReader,
) : ActivityEventProvider {
    override val providerId: String = PROVIDER_ID

    override fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            !context.allows(HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE)
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { rows ->
            rows.filter { it.organizationId == context.organizationId }.map { record ->
                val vehicle = listOf(record.vehicleName, record.vehicleType, record.plateNumber)
                    .map(String::trim).firstOrNull(String::isNotEmpty) ?: "سجل صيانة"
                ActivityEvent(
                    eventKey = ActivityEventKey.create(PROVIDER_ID, EVENT_CREATED, record.recordId),
                    organizationId = context.organizationId,
                    title = "إضافة سجل صيانة",
                    description = listOf(record.companyName, record.driverOrDelegate, record.notes)
                        .map(String::trim).filter(String::isNotEmpty).take(2)
                        .joinToString(" • ").ifEmpty { "تم تسجيل عملية صيانة" },
                    occurredAtEpochMillis = record.createdAtEpochMillis.coerceAtLeast(0L),
                    kind = ActivityEventKind.MAINTENANCE,
                    status = ActivityEventStatus.CREATED,
                    destination = HomeDestination(
                        id = HomeDestinationIds.OPTIMAL_MAINTENANCE_DETAILS,
                        arguments = mapOf("recordId" to record.recordId),
                    ),
                    subject = ActivityEventSubject(label = vehicle, id = record.recordId),
                    requiredPermission = HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE,
                )
            }
        })
    }

    companion object {
        const val PROVIDER_ID = "optimal.maintenance.activity"
        const val EVENT_CREATED = "maintenance_created"
        const val SOURCE_LIMIT = 100
    }
}
