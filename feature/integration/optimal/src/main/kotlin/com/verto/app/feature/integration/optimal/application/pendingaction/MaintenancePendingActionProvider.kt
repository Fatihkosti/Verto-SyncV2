package com.verto.app.feature.integration.optimal.application.pendingaction

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OperationalMaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.repository.MaintenanceFollowUpRepository
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionEventKey
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionProvider
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface MaintenancePendingActionClock {
    fun observeNowEpochMillis(): Flow<Long>
}

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenancePendingActionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MaintenanceFollowUpRepository,
    private val clock: MaintenancePendingActionClock,
    private val sessionReader: SessionReader,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id) {
            emit(emptyList())
            return@flow
        }
        if (!context.allows(HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE)) {
            emit(emptyList())
            return@flow
        }

        emitAll(
            clock.observeNowEpochMillis().flatMapLatest { now ->
                require(now >= 0L) { "nowEpochMillis must not be negative" }
                repository.observeOperational(context.organizationId, now).map { rows ->
                    rows.map { row -> row.toPendingAction() }
                        .sortedWith(PROVIDER_RANKING)
                }
            },
        )
    }

    private fun OperationalMaintenanceFollowUp.toPendingAction(): PendingAction {
        val eventType = if (overdue) EVENT_OVERDUE else EVENT_IN_PROGRESS
        val eventKey = PendingActionEventKey.create(PROVIDER_ID, eventType, followUp.recordId)
        val destination = HomeDestination(
            id = HomeDestinationIds.OPTIMAL_MAINTENANCE_DETAILS,
            arguments = mapOf("recordId" to followUp.recordId),
        )
        val vehicle = vehicleLabel()
        return PendingAction(
            eventKey = eventKey,
            title = if (overdue) context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_05c234ba811b_2) else context.getString(com.verto.feature.integration.optimal.R.string.optimal_v298_05c234ba811b),
            summary = if (overdue) {
                "$vehicle • تجاوزت الموعد المتوقع"
            } else {
                "$vehicle • المتابعة ما زالت قيد الصيانة"
            },
            occurredAtEpochMillis = (
                if (overdue) followUp.expectedAt ?: followUp.startedAt else followUp.startedAt
            ).coerceAtLeast(0L),
            priority = if (overdue) PendingActionPriority.HIGH else PendingActionPriority.NORMAL,
            destination = destination,
            actions = listOf(
                HomeAction(
                    id = "open_maintenance",
                    label = "فتح",
                    destination = destination,
                    requiredPermission = HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE,
                ),
                HomeAction(
                    id = "remind_later",
                    label = "تذكير",
                    destination = HomeDestination(
                        id = HomeDestinationIds.PENDING_ACTION_REMIND,
                        arguments = mapOf("eventKey" to eventKey),
                    ),
                    requiredPermission = HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE,
                ),
            ),
            requiredPermission = HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE,
        )
    }

    private fun OperationalMaintenanceFollowUp.vehicleLabel(): String {
        val primary = vehicleName.trim().ifEmpty { vehicleType.trim() }
        val plate = plateNumber.trim()
        return when {
            primary.isNotEmpty() && plate.isNotEmpty() -> "$primary • $plate"
            primary.isNotEmpty() -> primary
            plate.isNotEmpty() -> "لوحة $plate"
            else -> "سجل الصيانة"
        }
    }


    private val PROVIDER_RANKING: Comparator<PendingAction> =
        compareByDescending<PendingAction> { action -> action.priority.localRank }
            .thenBy { action -> action.occurredAtEpochMillis }
            .thenBy { action -> action.eventKey }

    private val PendingActionPriority.localRank: Int
        get() = when (this) {
            PendingActionPriority.LOW -> 0
            PendingActionPriority.NORMAL -> 1
            PendingActionPriority.HIGH -> 2
            PendingActionPriority.CRITICAL -> 3
        }

    companion object {
        const val PROVIDER_ID: String = "optimal.maintenance.pending"
        const val EVENT_IN_PROGRESS: String = "maintenance_in_progress"
        const val EVENT_OVERDUE: String = "maintenance_overdue"
    }
}
