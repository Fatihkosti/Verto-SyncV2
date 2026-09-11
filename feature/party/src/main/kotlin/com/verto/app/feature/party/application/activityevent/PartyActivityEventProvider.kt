package com.verto.app.feature.party.application.activityevent

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventActor
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

data class PartyActivityRecord(
    val id: String,
    val name: String,
    val isSupplier: Boolean,
    val createdAtEpochMillis: Long,
    val createdBy: String,
)

interface PartyActivityEventSource {
    fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<PartyActivityRecord>>
}

class PartyActivityEventProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val source: PartyActivityEventSource,
    private val sessionReader: SessionReader,
) : ActivityEventProvider {
    override val providerId: String = PROVIDER_ID

    override fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            !context.allows(HomePermissionKeys.CLIENTS_VIEW)
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { rows ->
            rows.map { record ->
                val type = if (record.isSupplier) "supplier_created" else "customer_created"
                ActivityEvent(
                    eventKey = ActivityEventKey.create(PROVIDER_ID, type, record.id),
                    organizationId = context.organizationId,
                    title = if (record.isSupplier) appContext.getString(com.verto.feature.party.R.string.party_v298_8e55f6b2d8d6_2) else appContext.getString(com.verto.feature.party.R.string.party_v298_8e55f6b2d8d6),
                    description = record.name.trim().ifEmpty { if (record.isSupplier) "مورد جديد" else "عميل جديد" },
                    occurredAtEpochMillis = record.createdAtEpochMillis.coerceAtLeast(0L),
                    kind = ActivityEventKind.PARTY,
                    status = ActivityEventStatus.CREATED,
                    destination = HomeDestination(
                        id = HomeDestinationIds.PARTY_DETAILS,
                        arguments = mapOf(
                            "partyId" to record.id,
                            "isSupplier" to record.isSupplier.toString(),
                        ),
                    ),
                    actor = record.createdBy.takeIf(String::isNotBlank)?.let { actorId ->
                        session.user.name.takeIf { actorId == session.user.id && it.isNotBlank() }
                            ?.let { ActivityEventActor(displayName = it, id = actorId) }
                    },
                    subject = ActivityEventSubject(label = record.name.trim().ifEmpty { appContext.getString(com.verto.feature.party.R.string.party_v298_ddf62c1c203a) }, id = record.id),
                    requiredPermission = HomePermissionKeys.CLIENTS_VIEW,
                )
            }
        })
    }

    companion object {
        const val PROVIDER_ID = "party.activity"
        const val SOURCE_LIMIT = 100
    }
}
