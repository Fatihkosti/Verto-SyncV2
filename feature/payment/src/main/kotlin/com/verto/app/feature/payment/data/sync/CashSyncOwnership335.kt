package com.verto.app.feature.payment.data.sync

import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncStage
import com.verto.app.data.sync.rollout.SyncAggregateOwnership
import com.verto.app.data.sync.rollout.SyncRolloutPolicy

/** Session 335 cutover fence: one transport owns expense/cash at a time. */
internal fun v2FinanceOwned335(organizationId: String): Boolean {
    val snapshot = SyncRolloutPolicy.snapshot(organizationId)
    return listOf("EXPENSE", "CASH_REGISTER", "CASH_MOVEMENT").all { aggregate ->
        SyncRolloutPolicy.ownership(snapshot, aggregate).ownership in V2_FINANCE_OWNER_STATES_335
    }
}

internal fun SyncOperation.isLegacyFinance335(): Boolean = when (stage) {
    SyncStage.PUSH -> order == 100 || order == 190
    SyncStage.DELETE -> order == 30
    SyncStage.PULL -> order == 100 || order == 200
    else -> false
}

private val V2_FINANCE_OWNER_STATES_335 = setOf(
    SyncAggregateOwnership.V2_AUTHORITATIVE,
    SyncAggregateOwnership.V2_PAUSED_SAFE,
    SyncAggregateOwnership.RETIRED_LEGACY,
)
