package com.verto.app.feature.shipment.application.pendingaction

import com.verto.app.core.session.domain.SessionReader
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class ShipmentReceiptIssueRecord(
    val receiptId: String,
    val shipmentId: String,
    val shipmentTitle: String,
    val missingItems: String,
    val damagedItems: String,
    val receivedAtEpochMillis: Long,
)

interface ShipmentReceiptIssuePendingActionSource {
    fun observeReceiptIssues(organizationId: String): Flow<List<ShipmentReceiptIssueRecord>>
}

class ShipmentReceiptIssuePendingActionProvider @Inject constructor(
    private val source: ShipmentReceiptIssuePendingActionSource,
    private val sessionReader: SessionReader,
) : PendingActionProvider {
    override val providerId: String = PROVIDER_ID

    override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id) {
            emit(emptyList())
            return@flow
        }
        if (!context.allows(HomePermissionKeys.SHIPMENTS_VIEW)) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            source.observeReceiptIssues(context.organizationId).map { rows ->
                rows.mapNotNull { it.toPendingAction() }
                    .sortedWith(PROVIDER_RANKING)
            },
        )
    }

    private fun ShipmentReceiptIssueRecord.toPendingAction(): PendingAction? {
        val missing = missingItems.trim()
        val damaged = damagedItems.trim()
        if (missing.isEmpty() && damaged.isEmpty()) return null
        val destination = HomeDestination(
            id = HomeDestinationIds.SHIPMENT_DETAILS,
            arguments = mapOf("shipmentId" to shipmentId),
        )
        val issueLabels = buildList {
            if (missing.isNotEmpty()) add("نقص")
            if (damaged.isNotEmpty()) add("تلف")
        }.joinToString(" و")
        return PendingAction(
            eventKey = PendingActionEventKey.create(PROVIDER_ID, EVENT_RECEIPT_ISSUE, receiptId),
            title = "بضاعة ناقصة أو تالفة عند الاستلام",
            summary = "$shipmentTitle • تم تسجيل $issueLabels في الاستلام",
            occurredAtEpochMillis = receivedAtEpochMillis.coerceAtLeast(0L),
            priority = if (missing.isNotEmpty()) PendingActionPriority.HIGH else PendingActionPriority.NORMAL,
            destination = destination,
            actions = listOf(
                HomeAction(
                    id = "open_shipment",
                    label = "فتح",
                    destination = destination,
                    requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
                ),
            ),
            requiredPermission = HomePermissionKeys.SHIPMENTS_VIEW,
        )
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
        const val PROVIDER_ID = "shipment.receipt.issue"
        const val EVENT_RECEIPT_ISSUE = "receipt_missing_or_damaged"
    }
}
