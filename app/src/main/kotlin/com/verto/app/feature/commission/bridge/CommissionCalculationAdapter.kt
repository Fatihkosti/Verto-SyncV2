package com.verto.app.feature.commission.bridge

import com.verto.app.data.local.entity.*
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.CommissionLedgerDto
import com.verto.app.data.remote.dto.MarketerBalanceDto
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.feature.commission.application.*
import com.verto.app.utils.MoneyMath
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.*

internal object CommissionCalculationAdapter {
    private fun com.verto.app.feature.party.domain.model.PartyClient.commissionClientType(): ClientType? =
        customerSegment?.name?.let { runCatching { ClientType.valueOf(it) }.getOrNull() }

    fun marketingClients(
        clients: List<com.verto.app.feature.party.domain.model.PartyClient>,
        pendingCodeClientIds: Set<String>,
        registeredMarketerIds: Set<String>,
    ): List<MarketingClient> = clients.filter { c ->
        c.customerSegment?.name in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
    }.map { c ->
        val linkStatus = when {
            c.id in registeredMarketerIds -> MarketerLinkStatus.REGISTERED
            c.id in pendingCodeClientIds -> MarketerLinkStatus.PENDING_CODE
            else -> MarketerLinkStatus.NOT_LINKED
        }
        val accountType = c.commissionClientType() ?: ClientType.MARKETER
        MarketingClient(
            id = c.id,
            name = c.name,
            typeLabel = accountType.label,
            accountType = accountType.name,
            linkStatus = linkStatus,
        )
    }.sortedBy { it.name }

    private fun parseIsoMillis(iso: String?): Long? = iso?.let {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(it.take(19))?.time
        }.getOrNull()
    }

    fun computeState(
        allInvoices: List<InvoiceEntity>,
        allClients: List<com.verto.app.feature.party.domain.model.PartyClient>,
        allCommPayments: List<CommissionPaymentEntity>,
        allInvoicePayments: List<PaymentEntity>,
        from: Long?,
        to: Long?,
        card: CommissionCard,
        marketingClients: List<MarketingClient> = emptyList(),
        serverLedgerRows: List<CommissionLedgerDto> = emptyList(),
        serverEligibility: List<CommissionEligibilityDto>? = null
    ): CommissionUiState {
        val clientMap = allClients.associateBy { it.id }
        val invoiceMap = allInvoices.associateBy { it.id }
        val paidPerInvoice = allInvoicePayments
            .groupBy { it.invoiceId }
            .mapValues { (_, pmts) -> with(MoneyMath) { pmts.map { it.amount }.moneySum() } }

        val eligLoaded = serverEligibility != null
        val eligAll = serverEligibility ?: emptyList()

        fun inRange(iso: String?): Boolean =
            if (from != null && to != null) parseIsoMillis(iso)?.let { it in from..to } ?: true else true

        val rangedRows = eligAll.filter { inRange(it.createdAt) }
        val withdrawRows = rangedRows.filter { it.eligibility == "WITHDRAWABLE" }
        val pendingRows = rangedRows.filter { it.eligibility == "PENDING" }
        val paidRows = rangedRows.filter { it.eligibility == "PAID" }

        val selectedRows = when (card) {
            CommissionCard.TOTAL -> rangedRows
            CommissionCard.WITHDRAWABLE -> withdrawRows
            CommissionCard.PENDING -> pendingRows
            CommissionCard.PAID -> paidRows
            CommissionCard.EARNING -> emptyList()
        }

        val filteredInvoices = selectedRows.mapNotNull { row ->
            val inv = invoiceMap[row.invoiceId] ?: return@mapNotNull null
            val client = clientMap[row.clientId]
            InvoiceWithClientName(
                inv.toCommissionInvoiceModel(),
                client?.name ?: "",
                client?.commissionClientType()?.label ?: ""
            )
        }

        val earningItems = buildEarningItems(
            ledgerRows = serverLedgerRows,
            invoiceMap = invoiceMap,
            clientMap = clientMap,
            paidPerInvoice = paidPerInvoice,
            from = from,
            to = to
        )

        val clientBalances = withdrawRows
            .groupBy { it.clientId }
            .map { (clientId, rows) ->
                val client = clientMap[clientId]
                val invs = rows.mapNotNull { invoiceMap[it.invoiceId] }.map { it.toCommissionInvoiceModel() }
                val totalForClient = with(MoneyMath) {
                    rangedRows.filter { it.clientId == clientId }.map { it.commission.toRemoteDouble() }.moneySum()
                }
                ClientCommissionBalance(
                    clientId = clientId,
                    clientName = client?.name ?: "",
                    totalCommission = totalForClient,
                    withdrawableCommission = with(MoneyMath) { rows.map { it.commission.toRemoteDouble() }.moneySum() },
                    withdrawableInvoices = invs
                )
            }
            .sortedByDescending { it.withdrawableCommission }

        return CommissionUiState(
            totalCommission = with(MoneyMath) { rangedRows.map { it.commission.toRemoteDouble() }.moneySum() },
            withdrawableCommission = with(MoneyMath) { withdrawRows.map { it.commission.toRemoteDouble() }.moneySum() },
            pendingCommission = with(MoneyMath) { pendingRows.map { it.commission.toRemoteDouble() }.moneySum() },
            paidCommission = with(MoneyMath) { paidRows.map { it.commission.toRemoteDouble() }.moneySum() },
            earningCommission = with(MoneyMath) { earningItems.map { it.earningAmount }.moneySum() },
            filteredInvoices = filteredInvoices,
            earningItems = earningItems,
            clientBalances = clientBalances,
            activityLog = filterActivityLog(allCommPayments, from, to).map { it.toCommissionActivityItem() },
            selectedCard = card,
            filterFrom = from,
            filterTo = to,
            marketingClients = marketingClients,
            isApproximate = !eligLoaded
        )
    }

    private fun filterActivityLog(
        activity: List<CommissionPaymentEntity>,
        from: Long?,
        to: Long?
    ): List<CommissionPaymentEntity> =
        if (from != null && to != null) activity.filter { it.paidAt in from..to } else activity

    private fun buildEarningItems(
        ledgerRows: List<CommissionLedgerDto>,
        invoiceMap: Map<String, InvoiceEntity>,
        clientMap: Map<String, com.verto.app.feature.party.domain.model.PartyClient>,
        paidPerInvoice: Map<String, Double>,
        from: Long?,
        to: Long?
    ): List<EarningCommissionItem> {
        val activeRows = ledgerRows.filter { row ->
            row.status == "EARNING" || row.status == "PENDING"
        }.filter { row ->
            MoneyMath.isGreaterThan(MoneyMath.subtract(row.amount.toRemoteDouble(), row.creditedAmount.toRemoteDouble()), 0.0)
        }.filter { row ->
            if (from != null && to != null) {
                parseIsoMillis(row.lastEventAt)?.let { it in from..to } ?: true
            } else {
                true
            }
        }

        return activeRows.map { row ->
            val invoice = invoiceMap[row.invoiceId]
            val client = clientMap[row.clientId]
            val paidAmount = invoice?.let { paidPerInvoice[it.id] ?: 0.0 } ?: 0.0
            val invoiceTotal = invoice?.totalAmount ?: 0.0
            val remainingAmount = if (invoice != null) {
                MoneyMath.subtract(invoiceTotal, paidAmount).coerceAtLeast(0.0)
            } else {
                0.0
            }

            EarningCommissionItem(
                invoiceId = row.invoiceId,
                invoiceNumber = invoice?.invoiceNumber,
                clientName = client?.name ?: "عميل غير معروف",
                clientTypeLabel = client?.commissionClientType()?.label ?: "",
                invoiceTotal = invoiceTotal,
                paidAmount = paidAmount,
                remainingAmount = remainingAmount,
                commissionAmount = row.amount.toRemoteDouble(),
                creditedAmount = row.creditedAmount.toRemoteDouble(),
                earningAmount = MoneyMath.subtract(row.amount.toRemoteDouble(), row.creditedAmount.toRemoteDouble()),
                status = row.status
            )
        }.sortedByDescending { it.earningAmount }
    }

    fun buildWithdrawalRequestItems(
        state: CommissionUiState,
        balances: List<MarketerBalanceDto>
    ): List<WithdrawalRequestUiModel> {
        val clientNames = state.marketingClients.associate { it.id to it.name }
        val balanceByClient = balances.associate { it.clientId to it.balance.toRemoteDouble() }
        val pendingTotalByClient = state.withdrawalRequests
            .filter { CommissionWithdrawalStatus.fromWire(it.status) == CommissionWithdrawalStatus.PENDING }
            .groupBy { it.clientId }
            .mapValues { (_, requests) ->
                with(MoneyMath) { requests.map { it.amount }.moneySum() }
            }

        return state.withdrawalRequests.map { request ->
            val balance = balanceByClient[request.clientId]
            val pendingTotal = pendingTotalByClient[request.clientId] ?: 0.0
            val balanceAvailable = balance != null
            WithdrawalRequestUiModel(
                request = request,
                status = CommissionWithdrawalStatus.fromWire(request.status),
                clientName = clientNames[request.clientId] ?: "مسوّق غير معروف",
                marketerBalance = balance,
                balanceState = if (balanceAvailable) MarketerBalanceState.AVAILABLE else MarketerBalanceState.UNAVAILABLE,
                pendingTotalForClient = pendingTotal,
                isAmountOverBalance = balance?.let { MoneyMath.isGreaterThan(request.amount, it) } ?: false,
                isPendingTotalOverBalance = balance?.let { MoneyMath.isGreaterThan(pendingTotal, it) } ?: false
            )
        }
    }

    fun currentMonthStart(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun currentMonthEnd(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59)
        set(java.util.Calendar.SECOND, 59); set(java.util.Calendar.MILLISECOND, 999)
    }.timeInMillis
}
