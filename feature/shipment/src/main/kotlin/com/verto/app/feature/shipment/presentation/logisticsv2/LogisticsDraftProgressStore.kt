package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Durable, session-scoped persistence boundary for the logistics planning wizard.
 * Implementations must isolate data by organization + user and reject unknown versions.
 */
interface LogisticsDraftProgressStore {
    suspend fun loadProgress(): LogisticsDraftProgress?
    suspend fun saveProgress(progress: LogisticsDraftProgress)
    suspend fun clearProgress(expectedShipmentId: String? = null)

    suspend fun loadRouteWorkspace(shipmentId: String): LogisticsRouteWorkspaceSnapshot?
    suspend fun saveRouteWorkspace(snapshot: LogisticsRouteWorkspaceSnapshot)
    suspend fun clearRouteWorkspace(shipmentId: String)

    suspend fun loadReceivingDraft(shipmentId: String): LogisticsFinalReceivingDraftSnapshot?
    suspend fun saveReceivingDraft(snapshot: LogisticsFinalReceivingDraftSnapshot)
    suspend fun clearReceivingDraft(shipmentId: String)

    suspend fun clearShipment(shipmentId: String) {
        clearProgress(expectedShipmentId = shipmentId)
        clearRouteWorkspace(shipmentId)
        clearReceivingDraft(shipmentId)
    }
}



data class LogisticsFinalReceivingLineSnapshot(
    val shipmentLineId: String,
    val sourceInvoiceId: String,
    val sourceInvoiceNumber: String,
    val itemName: String,
    val expectedQuantity: Int,
    val alreadyReceivedQuantity: Int,
)

data class LogisticsFinalReceivingDraftSnapshot(
    val shipmentId: String,
    /** Stable across process death so a partially committed finalization retries idempotently. */
    val requestId: String,
    val receivedCompletely: Boolean? = null,
    /** Blank strings are intentional: v241 requires shortage fields to start empty. */
    val missingQuantities: Map<String, String> = emptyMap(),
    /** Baseline before the first finalization attempt; do not replace with post-commit remaining quantities. */
    val lines: List<LogisticsFinalReceivingLineSnapshot> = emptyList(),
    val updatedAt: Long = 0L,
    val version: Int = CURRENT_VERSION,
) {
    companion object { const val CURRENT_VERSION = 1 }
}

data class LogisticsDraftProgress(
    val activeDraftShipmentId: String,
    val currentStep: LogisticsPlanningStep,
    val updatedAt: Long,
    val version: Int = CURRENT_VERSION,
) {
    companion object { const val CURRENT_VERSION = 1 }
}

data class LogisticsRouteWorkspaceSnapshot(
    val shipmentId: String,
    val newMilestoneLocation: String = "",
    val newMilestoneType: LogisticsMilestoneType = LogisticsMilestoneType.CUSTOMS,
    val incoterm: String = "",
    val volumeM3: String = "",
    val palletCount: String = "",
    val insuranceReference: String = "",
    /** v237: defaults are suggestions only until the user explicitly confirms them. */
    val routeTransportPlanKind: LogisticsRouteTransportPlanKind = LogisticsRouteTransportPlanKind.UNIFIED,
    val tripTypeSelected: Boolean = false,
    val unifiedTransportMode: LogisticsLegTransportMode = LogisticsLegTransportMode.ROAD,
    val unifiedTransportModeSelected: Boolean = false,
    /** Milestone ids whose intentional immediate duplicate has been confirmed by the planner. */
    val duplicateConfirmedMilestoneIds: Set<String> = emptySet(),
    /** v237 reuses this durable field as a route sub-screen cursor (trip type / builder / station). */
    val expandedSection: String = "V237_TRIP_TYPE",
    val templateName: String = "",
    /** v238 customs is a separate planned event after a station; never a milestone. */
    val customsCheckpointName: String = "",
    val customsAfterStationId: String = "",
    val customsExpectedDurationMinutes: Int? = null,
    val returnToReviewAfterEdit: Boolean = false,
    val milestones: List<LogisticsMilestone> = emptyList(),
    val legs: List<LogisticsShipmentLeg> = emptyList(),
    val pendingCosts: List<LogisticsCostDraft> = emptyList(),
    val pendingDocuments: List<LogisticsPendingDocumentDraft> = emptyList(),
    val updatedAt: Long = 0L,
    val version: Int = CURRENT_VERSION,
) {
    companion object { const val CURRENT_VERSION = 1 }
}


/** Debounced durable persistence for the route workspace. UI state is updated immediately in the ViewModel. */
internal class LogisticsRouteWorkspaceAutosaveCoordinator(
    private val scope: CoroutineScope,
    private val save: suspend (LogisticsRouteWorkspaceSnapshot) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private var debounceJob: Job? = null
    private val saveMutex = Mutex()
    private var revision: Long = 0L
    private var committedRevision: Long = 0L

    fun schedule(snapshot: LogisticsRouteWorkspaceSnapshot) {
        revision += 1L
        val targetRevision = revision
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(ROUTE_WORKSPACE_DEBOUNCE_MS)
            persist(snapshot, targetRevision)
        }
    }

    suspend fun flush(latest: () -> LogisticsRouteWorkspaceSnapshot?): Boolean {
        while (true) {
            debounceJob?.cancelAndJoin()
            debounceJob = null
            val snapshot = latest() ?: return true
            val targetRevision = revision
            if (!persist(snapshot, targetRevision)) return false
            if (revision == targetRevision) return true
        }
    }

    fun flushBestEffort(latest: () -> LogisticsRouteWorkspaceSnapshot?) {
        scope.launch { flush(latest) }
    }

    private suspend fun persist(snapshot: LogisticsRouteWorkspaceSnapshot, targetRevision: Long): Boolean =
        saveMutex.withLock {
            if (targetRevision <= committedRevision) return@withLock true
            val result = runCatching { save(snapshot) }
            if (result.isSuccess) committedRevision = targetRevision else result.exceptionOrNull()?.let(onFailure)
            result.isSuccess
        }

    private companion object {
        const val ROUTE_WORKSPACE_DEBOUNCE_MS = 400L
    }
}
