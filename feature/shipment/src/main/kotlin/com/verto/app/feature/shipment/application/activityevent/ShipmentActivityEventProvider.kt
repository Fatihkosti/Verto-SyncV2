package com.verto.app.feature.shipment.application.activityevent

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

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

data class ShipmentActivityRecord(
    val id: String,
    val title: String,
    val shipmentNumber: String,
    val origin: String,
    val destination: String,
    val createdAtEpochMillis: Long,
)

interface ShipmentActivityEventSource {
    fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<ShipmentActivityRecord>>
}

class ShipmentActivityEventProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val source: ShipmentActivityEventSource,
    private val sessionReader: SessionReader,
) : ActivityEventProvider {
    override val providerId: String = PROVIDER_ID

    override fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            !context.allows(HomePermissionKeys.SHIPMENTS_VIEW)
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { rows ->
            rows.map { record ->
                ActivityEvent(
                    eventKey = ActivityEventKey.create(PROVIDER_ID, EVENT_CREATED, record.id),
                    organizationId = context.organizationId,
                    title = "إضافة شحنة",
                    description = buildList {
                        record.shipmentNumber.trim().takeIf(String::isNotEmpty)?.let { add("رقم $it") }
                        record.origin.trim().takeIf(String::isNotEmpty)?.let { add(it) }
                        record.destination.trim().takeIf(String::isNotEmpty)?.let { add("إلى $it") }
                    }.joinToString(" • ").ifEmpty { "تم تسجيل شحنة جديدة" },
                    occurredAtEpochMillis = record.createdAtEpochMillis.coerceAtLeast(0L),
                    kind = ActivityEventKind.SHIPMENT,
                    status = ActivityEventStatus.CREATED,
                    destination = HomeDestination(
                        id = HomeDestinationIds.SHIPMENT_DETAILS,
                        arguments = mapOf("shipmentId" to record.id),
                    ),
                    subject = ActivityEventSubject(label = record.title.trim().ifEmpty { appContext.getString(com.verto.app.feature.shipment.R.string.shipment_v298_203650e7399a) }, id = record.id),
                    requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
                )
            }
        })
    }

    companion object {
        const val PROVIDER_ID = "shipment.activity"
        const val EVENT_CREATED = "shipment_created"
        const val SOURCE_LIMIT = 100
    }
}
