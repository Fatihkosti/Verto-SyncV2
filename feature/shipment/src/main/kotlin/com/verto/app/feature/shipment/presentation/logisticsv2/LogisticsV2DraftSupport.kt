package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsActualTimeTarget
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LogisticsDraftAutosaveCoordinator(
    private val scope: CoroutineScope,
    private val save: suspend (LogisticsShipmentHeaderDraft) -> LogisticsShipment,
    private val onResult: (Long, Result<LogisticsShipment>) -> Unit,
) {
    private var debounceJob: Job? = null
    private val saveMutex = Mutex()
    var revision: Long = 0L
        private set
    private var committedRevision: Long = 0L

    fun schedule(snapshot: LogisticsShipmentHeaderDraft) {
        revision += 1L
        val scheduledRevision = revision
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            scope.launch { persist(snapshot, scheduledRevision) }
        }
    }

    suspend fun flush(latest: () -> LogisticsShipmentHeaderDraft?): Boolean {
        while (true) {
            debounceJob?.cancelAndJoin()
            debounceJob = null
            val snapshot = latest() ?: return true
            val targetRevision = revision
            if (!persist(snapshot, targetRevision)) return false
            if (revision == targetRevision) return true
        }
    }

    fun flushBestEffort(latest: () -> LogisticsShipmentHeaderDraft?) {
        scope.launch { flush(latest) }
    }

    private suspend fun persist(snapshot: LogisticsShipmentHeaderDraft, targetRevision: Long): Boolean =
        saveMutex.withLock {
            if (targetRevision <= committedRevision) return@withLock true
            val result = runCatching { save(snapshot) }
            if (result.isSuccess) committedRevision = targetRevision
            onResult(targetRevision, result)
            result.isSuccess
        }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
    }
}

internal class LogisticsPurchasePlanAutosaveCoordinator(
    private val scope: CoroutineScope,
    private val save: suspend (LogisticsPlanningDraft) -> LogisticsShipment,
    private val onResult: (Long, Result<LogisticsShipment>) -> Unit,
) {
    private var debounceJob: Job? = null
    private val saveMutex = Mutex()
    var revision: Long = 0L
        private set
    private var committedRevision: Long = 0L

    fun schedule(snapshot: LogisticsPlanningDraft) {
        revision += 1L
        val scheduledRevision = revision
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(PURCHASE_SAVE_DEBOUNCE_MS)
            scope.launch { persist(snapshot, scheduledRevision) }
        }
    }

    suspend fun flush(latest: () -> LogisticsPlanningDraft?): Boolean {
        while (true) {
            debounceJob?.cancelAndJoin()
            debounceJob = null
            val snapshot = latest() ?: return true
            val targetRevision = revision
            if (!persist(snapshot, targetRevision)) return false
            if (revision == targetRevision) return true
        }
    }

    fun flushBestEffort(latest: () -> LogisticsPlanningDraft?) {
        scope.launch { flush(latest) }
    }

    private suspend fun persist(snapshot: LogisticsPlanningDraft, targetRevision: Long): Boolean =
        saveMutex.withLock {
            if (targetRevision <= committedRevision) return@withLock true
            val result = runCatching { save(snapshot) }
            if (result.isSuccess) committedRevision = targetRevision
            onResult(targetRevision, result)
            result.isSuccess
        }

    private companion object {
        const val PURCHASE_SAVE_DEBOUNCE_MS = 400L
    }
}

data class LogisticsPaidCostEditDraft(
    val amount: String,
    val currency: String,
    val exchangeRate: String,
    val description: String,
    val note: String = "",
)

internal data class LogisticsCostProofDraft(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
)

data class LogisticsUnplannedLocationDraft(
    val countryCode: String, val countryName: String, val city: String, val placeName: String,
)

data class LogisticsUnplannedStationDraft(
    val location: LogisticsUnplannedLocationDraft,
    val stationType: LogisticsMilestoneType,
    val reason: String,
    val continuationCarrierPartnerId: String,
    val continuationMode: LogisticsLegTransportMode,
) {
    val isValid: Boolean get() = location.countryCode.isNotBlank() &&
        location.countryName.isNotBlank() && location.city.isNotBlank() && location.placeName.isNotBlank() && reason.isNotBlank() &&
        stationType in setOf(LogisticsMilestoneType.TRANSIT, LogisticsMilestoneType.CUSTOMS) && continuationCarrierPartnerId.isNotBlank()
}

data class LogisticsActualTimeCorrectionDraft(
    val milestoneId: String,
    val target: LogisticsActualTimeTarget,
    val correctedAt: Long,
    val reason: String,
) {
    val isValid: Boolean get() = milestoneId.isNotBlank() && correctedAt >= 0L && reason.isNotBlank()
}

data class LogisticsRecoveryLineDraft(
    val shortageId: String,
    val recoveredQuantity: String,
) {
    val quantity: Int? get() = recoveredQuantity.toIntOrNull()
    val isSelected: Boolean get() = (quantity ?: 0) > 0
}

data class LogisticsRecoveryCostDraft(
    val type: LogisticsCostType,
    val amount: String = "",
    val currency: String = "SDG",
    val exchangeRate: String = "1",
    val description: String,
    val confirmPaid: Boolean = false,
) {
    val amountDecimal: BigDecimal? get() = amount.toBigDecimalOrNull()
    val rateDecimal: BigDecimal? get() = exchangeRate.toBigDecimalOrNull()
    val isEmpty: Boolean get() = amount.isBlank()
    val isValid: Boolean get() = isEmpty || (amountDecimal?.signum() == 1 && rateDecimal?.signum() == 1 && currency.isNotBlank() && description.isNotBlank())
}

data class LogisticsRecoveryDraft(
    val lines: List<LogisticsRecoveryLineDraft>,
    val noteOrLocation: String,
    val costs: List<LogisticsRecoveryCostDraft>,
) {
    val isValid: Boolean get() = noteOrLocation.isNotBlank() && lines.any { it.isSelected } && costs.all { it.isValid }
}

data class LogisticsShortageSettlementDraft(
    val shortageId: String,
    val type: LogisticsShortageSettlementType,
    val quantity: Int,
    val compensationAmount: String = "",
    val currency: String = "SDG",
    val exchangeRate: String = "1",
    val note: String = ""
) {
    val isValid: Boolean get() = shortageId.isNotBlank() && quantity > 0 && when (type) {
        LogisticsShortageSettlementType.COMPENSATED -> compensationAmount.toBigDecimalOrNull()?.signum() == 1 && exchangeRate.toBigDecimalOrNull()?.signum() == 1 && currency.isNotBlank()
        LogisticsShortageSettlementType.FINAL_LOSS -> true
    }
}

internal fun LogisticsCenterUiState.withClosedShortageLabels(
    aggregates: Map<String, LogisticsShipmentAggregate>,
): LogisticsCenterUiState {
    val content = this as? LogisticsCenterUiState.Content ?: return this
    return content.copy(
        groups = content.groups.mapValues { (_, cards) ->
            cards.map { card ->
                val hasOpenShortage = aggregates[card.id]?.shortages?.any { it.quantity.remainingMissingQuantity > 0 } == true
                if (card.state == LogisticsShipmentState.CLOSED && hasOpenShortage) card.copy(stateLabel = "مغلقة مع نقص") else card
            }
        },
    )
}
