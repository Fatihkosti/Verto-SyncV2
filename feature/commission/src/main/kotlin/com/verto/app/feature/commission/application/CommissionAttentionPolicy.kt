package com.verto.app.feature.commission.application

/** Pure read policy for the financial items that require an administrator action. */
object CommissionAttentionPolicy {
    fun build(state: CommissionUiState): CommissionAttentionSummary {
        val openRequests = state.withdrawalRequestItems
            .filter { item ->
                item.status == CommissionWithdrawalStatus.PENDING ||
                    item.status == CommissionWithdrawalStatus.APPROVED
            }

        val openClientIds = openRequests.map { it.request.clientId }.toSet()

        val withdrawalItems = openRequests
            .sortedWith(
                compareBy<WithdrawalRequestUiModel> { statusOrder(it.status) }
                    .thenByDescending { it.request.requestedAt },
            )
            .map { item ->
                CommissionAttentionItem.WithdrawalRequest(
                    requestId = item.request.id,
                    clientId = item.request.clientId,
                    clientName = item.clientName,
                    amount = item.request.amount,
                    status = item.status,
                    requestedAt = item.request.requestedAt,
                )
            }

        val readyPayoutItems = state.clientBalances
            .asSequence()
            .filter { balance -> balance.withdrawableCommission > 0.0 }
            .filterNot { balance -> balance.clientId in openClientIds }
            .sortedByDescending { balance -> balance.withdrawableCommission }
            .map { balance ->
                CommissionAttentionItem.ReadyPayout(
                    clientId = balance.clientId,
                    clientName = balance.clientName,
                    amount = balance.withdrawableCommission,
                    invoicesCount = balance.withdrawableInvoices.size,
                )
            }
            .toList()

        return CommissionAttentionSummary(
            openWithdrawalRequestsCount = openRequests.size,
            openWithdrawalRequestsAmount = openRequests.sumOf { it.request.amount },
            items = withdrawalItems + readyPayoutItems,
        )
    }

    private fun statusOrder(status: CommissionWithdrawalStatus): Int = when (status) {
        CommissionWithdrawalStatus.PENDING -> 0
        CommissionWithdrawalStatus.APPROVED -> 1
        else -> 2
    }
}
