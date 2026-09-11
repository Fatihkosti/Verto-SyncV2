package com.verto.app.feature.commission.bridge

import com.verto.app.data.local.dao.CommissionPaymentDao
import com.verto.app.data.local.dao.JoinCodeDao
import com.verto.app.data.local.entity.*
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.CommissionLedgerDto
import com.verto.app.data.remote.dto.MarketerBalanceDto
import com.verto.app.data.remote.dto.WithdrawalRequestDto
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.commission.application.*
import kotlinx.coroutines.flow.*

internal data class CommissionSourceData(
    val invoices: List<InvoiceEntity>,
    val clients: List<PartyClient>,
    val commissionPayments: List<CommissionPaymentEntity>,
    val invoicePayments: List<PaymentEntity>,
    val joinCodes: List<JoinCodeEntity>
)

internal data class CommissionServerData(
    val ledgerRows: List<CommissionLedgerDto> = emptyList(),
    val registeredMarketerIds: Set<String> = emptySet(),
    val eligibility: List<CommissionEligibilityDto>? = null
)

internal fun InvoiceEntity.toCommissionInvoiceModel() = CommissionInvoiceModel(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    totalAmount = totalAmount,
    commission = commission,
    createdAt = createdAt
)

internal fun CommissionPaymentEntity.toCommissionActivityItem() = CommissionActivityItem(
    id = id,
    clientId = clientId,
    clientName = clientName,
    invoiceIds = invoiceIds,
    totalAmount = totalAmount,
    bankName = bankName,
    transactionRef = transactionRef,
    paidAt = paidAt
)

internal fun WithdrawalRequestDto.toCommissionWithdrawalRequest() = CommissionWithdrawalRequest(
    id = id,
    clientId = clientId,
    amount = amount.toRemoteDouble(),
    status = status,
    bankName = bankName,
    bankAccount = bankAccount,
    transactionRef = transactionRef,
    note = note,
    adminNote = adminNote,
    requestedAt = requestedAt
)

internal class CommissionQueryAdapter(
    private val invoiceRepository: InvoiceRepository,
    private val partyDirectory: PartyDirectoryGateway,
    private val commissionPaymentDao: CommissionPaymentDao,
    private val joinCodeDao: JoinCodeDao,
    private val withdrawalRepo: WithdrawalRepository,
) {
    val rawFlow = combine(
        invoiceRepository.observeAllInvoices(),
        partyDirectory.getAllClients(),
        commissionPaymentDao.getAll(),
        invoiceRepository.observeAllPayments(),
        joinCodeDao.getAll(),
    ) { invoices, clients, commPmts, invPmts, joinCodes ->
        CommissionSourceData(invoices.filter { !it.voided }, clients, commPmts, invPmts, joinCodes)
    }

    suspend fun loadServerData(
        onEligibility: (List<CommissionEligibilityDto>) -> Unit,
        onLedger: (List<CommissionLedgerDto>) -> Unit,
        onRegisteredMarketerIds: (Set<String>) -> Unit,
    ) {
        withdrawalRepo.getCommissionEligibility()
            .onSuccess(onEligibility)
            .onFailure { e -> android.util.Log.e("CommissionVM", "loadEligibility failed: ${e::class.java.simpleName}") }
        withdrawalRepo.getCommissionLedger()
            .onSuccess(onLedger)
            .onFailure { e -> android.util.Log.e("CommissionVM", "loadLedger failed: ${e::class.java.simpleName}") }
        withdrawalRepo.getRegisteredMarketers()
            .onSuccess { rows -> onRegisteredMarketerIds(rows.map { it.clientId }.toSet()) }
            .onFailure { e -> android.util.Log.e("CommissionVM", "loadRegisteredMarketers failed: ${e::class.java.simpleName}") }
    }

    suspend fun refreshWithdrawalStateFromServer(
        onRequests: (List<WithdrawalRequestDto>) -> Unit,
        onBalances: (List<MarketerBalanceDto>) -> Unit,
        onEligibility: (List<CommissionEligibilityDto>) -> Unit,
        onLedger: (List<CommissionLedgerDto>) -> Unit,
    ): List<WithdrawalRequestDto>? {
        val requests = withdrawalRepo.getWithdrawalRequests().onSuccess(onRequests)
        withdrawalRepo.getMarketerBalances()
            .onSuccess(onBalances)
            .onFailure { error ->
                // Never keep an old balance after a failed refresh: absence means unavailable, not zero.
                onBalances(emptyList())
                android.util.Log.e("CommissionVM", "loadBalances failed: ${error::class.java.simpleName}")
            }
        withdrawalRepo.getCommissionEligibility().onSuccess(onEligibility)
        withdrawalRepo.getCommissionLedger().onSuccess(onLedger)
        return requests.getOrNull()
    }
}
