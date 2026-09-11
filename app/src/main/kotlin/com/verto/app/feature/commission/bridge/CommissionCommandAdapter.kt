package com.verto.app.feature.commission.bridge

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.error.AppFailure
import com.verto.app.data.local.entity.*
import com.verto.app.feature.commission.application.*
import com.verto.app.utils.FeatureFlags
import com.verto.app.utils.MoneyMath
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class CommissionCommandAdapter(
    private val scope: CoroutineScope,
    private val auditLogger: WriteAuditPort,
) {
    private val payoutRequestIds = mutableMapOf<String, String>()

    fun payoutRequestId(operation: String): String =
        payoutRequestIds.getOrPut(operation) { UUID.randomUUID().toString() }

    fun completePayoutRequest(operation: String) {
        payoutRequestIds.remove(operation)
    }

    fun logCommissionAudit(summary: String, amount: Double, recordId: String) {
        scope.launch {
            runCatching {
                auditLogger.log(
                    action = AuditAction.INSERT,
                    table = AuditTable.COMMISSION,
                    recordId = recordId,
                    summary = summary,
                    newValue = MoneyMath.round(amount).toString(),
                    canUndo = false,
                )
            }
        }
    }

    fun ensureFinancialMutationsEnabled(onDisabled: (AppFailure) -> Unit): Boolean {
        if (FeatureFlags.isFinancialMutationsEnabled) return true
        onDisabled(AppFailure.BusinessRule(code = "FINANCIAL_MUTATIONS_DISABLED"))
        return false
    }

    fun logWithdrawalAudit(
        action: AuditAction,
        requestId: String,
        summary: String,
        amount: Double,
    ) {
        scope.launch {
            runCatching {
                auditLogger.log(
                    action = action,
                    table = AuditTable.WITHDRAWAL,
                    recordId = requestId,
                    summary = summary,
                    newValue = MoneyMath.round(amount).toString(),
                    canUndo = false,
                )
            }
        }
    }
}
