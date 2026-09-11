package com.verto.app.feature.commission.bridge

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.OperationOutcome
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.data.local.dao.CommissionPaymentDao
import com.verto.app.data.local.dao.JoinCodeDao
import com.verto.app.data.local.entity.*
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.CommissionLedgerDto
import com.verto.app.data.remote.dto.MarketerBalanceDto
import com.verto.app.data.remote.dto.WithdrawalRequestDto
import com.verto.app.data.remote.dto.WithdrawalStatus
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.data.sync.RealtimeManager
import com.verto.app.data.sync.SyncManager
import com.verto.app.feature.commission.application.*
import com.verto.app.pdf.buildMarketerCommissionReport
import com.verto.app.utils.CashRegisterManager
import com.verto.app.utils.MoneyMath
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.UserErrorFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Singleton
class DefaultCommissionControllerFactory @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val partyDirectory: PartyDirectoryGateway,
    private val commissionPaymentDao: CommissionPaymentDao,
    private val cashRegisterManager: CashRegisterManager,
    private val joinCodeDao: JoinCodeDao,
    private val authRepository: AuthRepository,
    private val withdrawalRepo: WithdrawalRepository,
    private val syncManager: SyncManager,
    private val realtimeManager: RealtimeManager,
    private val auditLogger: WriteAuditPort,
    private val documentSharePort: DocumentSharePort,
    private val userPrefs: PreferencesManager,
    private val permissionProvider: PermissionProvider,
    private val roleProvider: RoleProvider
) : CommissionControllerFactory {
    override fun create(scope: CoroutineScope): CommissionController = Controller(scope)

    private inner class Controller(
        private val scope: CoroutineScope
    ) : CommissionController {

    override val role: StateFlow<String?> = roleProvider.role
    override val permissions: StateFlow<CommissionPermissions?> = permissionProvider.permissions
        .map { value -> value?.let { CommissionPermissions(it.commissionManage, it.marketingDashboards) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val queryAdapter = CommissionQueryAdapter(
        invoiceRepository = invoiceRepository,
        partyDirectory = partyDirectory,
        commissionPaymentDao = commissionPaymentDao,
        joinCodeDao = joinCodeDao,
        withdrawalRepo = withdrawalRepo,
    )
    private val calculationAdapter = CommissionCalculationAdapter
    private val commandAdapter = CommissionCommandAdapter(scope, auditLogger)
    private val rawFlow = queryAdapter.rawFlow

    private val filterFrom = MutableStateFlow<Long?>(calculationAdapter.currentMonthStart())
    private val filterTo = MutableStateFlow<Long?>(calculationAdapter.currentMonthEnd())
    private val selectedCard = MutableStateFlow(CommissionCard.TOTAL)

    private val _paymentSuccess = MutableStateFlow(false)
    private val _generatedCode = MutableStateFlow<Pair<String, String>?>(null)
    private val _isGeneratingCode = MutableStateFlow(false)
    private val _codeError = MutableStateFlow<UserErrorPresentation?>(null)
    private val _withdrawalRequests = MutableStateFlow<List<WithdrawalRequestDto>>(emptyList())
    private val _marketerBalances = MutableStateFlow<List<MarketerBalanceDto>>(emptyList())
    private val _requestActionError = MutableStateFlow<UserErrorPresentation?>(null)
    private val _isPayingOut = MutableStateFlow(false)
    private val _isRequestActionInProgress = MutableStateFlow(false)
    private val _serverEligibility = MutableStateFlow<List<CommissionEligibilityDto>?>(null)
    private val _serverLedger = MutableStateFlow<List<CommissionLedgerDto>>(emptyList())
    private val _registeredMarketerIds = MutableStateFlow<Set<String>>(emptySet())

    override val uiState: StateFlow<CommissionUiState> = combine(
        rawFlow,
        filterFrom,
        filterTo,
        selectedCard,
        combine(_serverLedger, _registeredMarketerIds, _serverEligibility) { ledgerRows, registeredIds, eligibility ->
            CommissionServerData(ledgerRows, registeredIds, eligibility)
        }
    ) { raw, from, to, card, serverData ->
        val clients = raw.clients
        val pendingCodeClientIds = raw.joinCodes
            .filter { !it.used && it.expiresAt >= System.currentTimeMillis() }
            .map { it.clientId }
            .toSet()
        val marketing = calculationAdapter.marketingClients(
            clients = clients,
            pendingCodeClientIds = pendingCodeClientIds,
            registeredMarketerIds = serverData.registeredMarketerIds,
        )
        calculationAdapter.computeState(
            allInvoices = raw.invoices,
            allClients = clients,
            allCommPayments = raw.commissionPayments,
            allInvoicePayments = raw.invoicePayments,
            from = from,
            to = to,
            card = card,
            marketingClients = marketing,
            serverLedgerRows = serverData.ledgerRows,
            serverEligibility = serverData.eligibility,
        )
    }.combine(_paymentSuccess) { state, success ->
        state.copy(paymentSuccess = success)
    }.combine(_generatedCode) { state, codeInfo ->
        state.copy(
            generatedCode = codeInfo?.first,
            generatedCodeClientName = codeInfo?.second ?: "",
        )
    }.combine(_isGeneratingCode) { state, generating ->
        state.copy(isGeneratingCode = generating)
    }.combine(_codeError) { state, err ->
        state.copy(codeError = err)
    }.combine(_withdrawalRequests) { state, requests ->
        state.copy(
            withdrawalRequests = requests.map { it.toCommissionWithdrawalRequest() },
            pendingRequestsCount = requests.count { WithdrawalStatus.fromWire(it.status) == WithdrawalStatus.PENDING },
        )
    }.combine(_marketerBalances) { state, balances ->
        state.copy(withdrawalRequestItems = calculationAdapter.buildWithdrawalRequestItems(state, balances))
    }.combine(_requestActionError) { state, err ->
        state.copy(requestActionError = err)
    }.combine(_isPayingOut) { state, paying ->
        state.copy(isPayingOut = paying)
    }.combine(_isRequestActionInProgress) { state, inProgress ->
        state.copy(isRequestActionInProgress = inProgress)
    }.map { state ->
        state.copy(attention = CommissionAttentionPolicy.build(state))
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), CommissionUiState())

    override fun dispatch(action: CommissionAction) {
        when (action) {
            is CommissionAction.SetFilter -> setFilter(action.from, action.to)
            CommissionAction.ClearFilter -> clearFilter()
            CommissionAction.ShowAllPeriods -> showAllPeriods()
            is CommissionAction.SelectCard -> selectCard(action.card)
            CommissionAction.ClearPaymentSuccess -> clearPaymentSuccess()
            CommissionAction.ClearRequestActionError -> clearRequestActionError()
            CommissionAction.LoadServerData -> loadServerData()
            CommissionAction.LoadWithdrawalRequests -> loadWithdrawalRequests()
            is CommissionAction.WithdrawAll -> withdrawAll(
                action.clientId, action.clientName, action.bankName, action.transactionRef
            )
            is CommissionAction.WithdrawSingle -> withdrawSingle(
                action.invoice, action.clientName, action.bankName, action.transactionRef
            )
            is CommissionAction.WithdrawFreeAmount -> withdrawFreeAmount(
                action.clientId, action.clientName, action.amount, action.bankName, action.transactionRef
            )
            is CommissionAction.ApproveRequest -> approveRequest(action.id, action.transactionRef)
            is CommissionAction.RejectRequest -> rejectRequest(action.id, action.adminNote)
            is CommissionAction.CompleteRequest -> completeRequest(action.id, action.adminNote)
            is CommissionAction.GenerateJoinCode -> generateJoinCode(action.client)
            CommissionAction.ClearCodeError -> clearCodeError()
            CommissionAction.ClearGeneratedCode -> clearGeneratedCode()
            is CommissionAction.DeleteCommissionPayment -> deleteCommissionPayment(action.id)
        }
    }

    init {
        scope.launch { loadWithdrawalRequests() }
        scope.launch { loadServerData() }
        scope.launch {
            syncManager.syncCompleted.collect {
                loadWithdrawalRequests()
                loadServerData()
            }
        }
        scope.launch {
            realtimeManager.withdrawalRequestsChanged.collect {
                loadWithdrawalRequests()
            }
        }
    }

    fun loadServerData() = scope.launch {
        queryAdapter.loadServerData(
            onEligibility = { _serverEligibility.value = it },
            onLedger = { _serverLedger.value = it },
            onRegisteredMarketerIds = { _registeredMarketerIds.value = it },
        )
    }

    fun setFilter(from: Long, to: Long) { filterFrom.value = from; filterTo.value = to }
    fun clearFilter() { filterFrom.value = calculationAdapter.currentMonthStart(); filterTo.value = calculationAdapter.currentMonthEnd() }
    fun showAllPeriods() { filterFrom.value = null; filterTo.value = null }
    fun selectCard(c: CommissionCard) { selectedCard.value = c }
    fun clearPaymentSuccess() { _paymentSuccess.value = false }
    fun clearRequestActionError() { _requestActionError.value = null }

    fun withdrawAll(clientId: String, clientName: String, bankName: String, txRef: String) =
        scope.launch {
            if (!commandAdapter.ensureFinancialMutationsEnabled { failure ->
                    _requestActionError.value = present(failure, "payout_commission_guard")
                }) return@launch
            if (!permissionProvider.canNow { it.commissionManage }) {
                _requestActionError.value = permissionError("payout_commission")
                return@launch
            }
            if (_isPayingOut.value) return@launch
            val invs = uiState.value.clientBalances
                .find { it.clientId == clientId }?.withdrawableInvoices ?: return@launch
            val total = with(MoneyMath) { invs.map { it.commission }.moneySum() }
            val operationKey = "all:$clientId:${invs.map { it.id }.sorted().joinToString(",")}:$txRef"
            val clientRequestId = commandAdapter.payoutRequestId(operationKey)

            _isPayingOut.value = true
            withdrawalRepo.payOutCommission(
                invoiceIds = invs.map { it.id },
                bankName = bankName,
                txRef = txRef,
                clientRequestId = clientRequestId,
            ).onSuccess {
                commandAdapter.completePayoutRequest(operationKey)
                _requestActionError.value = null
                _paymentSuccess.value = true
                refreshCommissionPayments()
                commandAdapter.logCommissionAudit("صرف عمولات — $clientName", MoneyMath.round(total), clientRequestId)
            }.onFailure { error ->
                // Do not retire clientRequestId. A later user-initiated verification/replay with the
                // same operation key remains idempotent instead of creating a second financial command.
                _requestActionError.value = present(
                    error,
                    "payout_commission",
                    uncertainFinancialOutcome(error),
                )
                refreshCommissionPayments()
            }
            _isPayingOut.value = false
        }

    fun withdrawSingle(invoice: CommissionInvoiceModel, clientName: String, bankName: String, txRef: String) =
        scope.launch {
            if (!commandAdapter.ensureFinancialMutationsEnabled { failure ->
                    _requestActionError.value = present(failure, "payout_commission_guard")
                }) return@launch
            if (!permissionProvider.canNow { it.commissionManage }) {
                _requestActionError.value = permissionError("payout_commission")
                return@launch
            }
            if (_isPayingOut.value) return@launch
            val operationKey = "invoice:${invoice.id}:$txRef"
            val clientRequestId = commandAdapter.payoutRequestId(operationKey)

            _isPayingOut.value = true
            withdrawalRepo.payOutCommission(
                invoiceIds = listOf(invoice.id),
                bankName = bankName,
                txRef = txRef,
                clientRequestId = clientRequestId,
            ).onSuccess {
                commandAdapter.completePayoutRequest(operationKey)
                _requestActionError.value = null
                _paymentSuccess.value = true
                refreshCommissionPayments()
                commandAdapter.logCommissionAudit(
                    "صرف عمولة فاتورة #${invoice.invoiceNumber} — $clientName",
                    MoneyMath.round(invoice.commission),
                    clientRequestId,
                )
            }.onFailure { error ->
                _requestActionError.value = present(
                    error,
                    "payout_commission",
                    uncertainFinancialOutcome(error),
                )
                refreshCommissionPayments()
            }
            _isPayingOut.value = false
        }

    fun withdrawFreeAmount(
        clientId: String,
        clientName: String,
        amount: Double,
        bankName: String,
        txRef: String
    ) = scope.launch {
        if (!commandAdapter.ensureFinancialMutationsEnabled { failure ->
                _requestActionError.value = present(failure, "payout_commission_guard")
            }) return@launch
        if (!permissionProvider.canNow { it.commissionManage }) {
            _requestActionError.value = permissionError("payout_commission")
            return@launch
        }
        if (_isPayingOut.value) return@launch
        val operationKey = "free:$clientId:${MoneyMath.round(amount)}:$txRef"
        val clientRequestId = commandAdapter.payoutRequestId(operationKey)

        _isPayingOut.value = true
        withdrawalRepo.payOutFreeAmount(
            clientId = clientId,
            amount = amount,
            bankName = bankName,
            txRef = txRef,
            clientRequestId = clientRequestId,
        ).onSuccess {
            commandAdapter.completePayoutRequest(operationKey)
            _requestActionError.value = null
            _paymentSuccess.value = true
            refreshCommissionPayments()
            commandAdapter.logCommissionAudit("صرف مبلغ حر — $clientName", MoneyMath.round(amount), clientRequestId)
        }.onFailure { error ->
            _requestActionError.value = present(
                error,
                "payout_commission",
                uncertainFinancialOutcome(error),
            )
            refreshCommissionPayments()
        }
        _isPayingOut.value = false
    }

    private suspend fun refreshWithdrawalStateFromServer(): List<WithdrawalRequestDto>? =
        queryAdapter.refreshWithdrawalStateFromServer(
            onRequests = { _withdrawalRequests.value = it },
            onBalances = { _marketerBalances.value = it },
            onEligibility = { _serverEligibility.value = it },
            onLedger = { _serverLedger.value = it },
        )

    fun loadWithdrawalRequests() = scope.launch {
        refreshWithdrawalStateFromServer()
    }

    fun approveRequest(id: String, transactionRef: String) = scope.launch {
        if (!permissionProvider.canNow { it.commissionManage }) {
            _requestActionError.value = permissionError("approve_withdrawal")
            return@launch
        }
        if (_isRequestActionInProgress.value) return@launch
        val request = _withdrawalRequests.value.find { it.id == id }
        val balance = request?.let { req ->
            _marketerBalances.value.find { it.clientId == req.clientId }?.balance?.toRemoteDouble()
        }
        if (request != null && balance == null) {
            _requestActionError.value = present(
                AppFailure.BusinessRule(code = "MARKETER_BALANCE_UNAVAILABLE"),
                "approve_withdrawal",
            )
            loadWithdrawalRequests()
            return@launch
        }
        if (request != null && balance != null && MoneyMath.isGreaterThan(request.amount.toRemoteDouble(), balance)) {
            _requestActionError.value = present(
                AppFailure.BusinessRule(code = "WITHDRAWAL_AMOUNT_EXCEEDS_BALANCE", target = "amount"),
                "approve_withdrawal",
            )
            loadWithdrawalRequests()
            return@launch
        }
        _isRequestActionInProgress.value = true
        withdrawalRepo.approveRequest(id, transactionRef)
            .onSuccess {
                _requestActionError.value = null
                refreshWithdrawalStateFromServer()
                commandAdapter.logWithdrawalAudit(
                    AuditAction.UPDATE,
                    id,
                    "اعتماد طلب سحب",
                    request?.amount?.toRemoteDouble() ?: 0.0,
                )
            }
            .onFailure { error ->
                val refreshed = refreshWithdrawalStateFromServer()
                if (hasStatus(refreshed, id, WithdrawalStatus.APPROVED)) {
                    _requestActionError.value = null
                    commandAdapter.logWithdrawalAudit(
                        AuditAction.UPDATE,
                        id,
                        "اعتماد طلب سحب",
                        request?.amount?.toRemoteDouble() ?: 0.0,
                    )
                } else {
                    _requestActionError.value = present(
                        error,
                        "approve_withdrawal",
                        verifiedOutcome(error, refreshed),
                    )
                }
            }
        _isRequestActionInProgress.value = false
    }

    fun rejectRequest(id: String, adminNote: String) = scope.launch {
        if (!permissionProvider.canNow { it.commissionManage }) {
            _requestActionError.value = permissionError("reject_withdrawal")
            return@launch
        }
        if (_isRequestActionInProgress.value) return@launch
        val amount = _withdrawalRequests.value.find { it.id == id }?.amount?.toRemoteDouble() ?: 0.0
        _isRequestActionInProgress.value = true
        withdrawalRepo.rejectRequest(id, adminNote)
            .onSuccess {
                _requestActionError.value = null
                refreshWithdrawalStateFromServer()
                commandAdapter.logWithdrawalAudit(AuditAction.UPDATE, id, "رفض طلب سحب: $adminNote", amount)
            }
            .onFailure { error ->
                val refreshed = refreshWithdrawalStateFromServer()
                if (hasStatus(refreshed, id, WithdrawalStatus.REJECTED)) {
                    _requestActionError.value = null
                    commandAdapter.logWithdrawalAudit(AuditAction.UPDATE, id, "رفض طلب سحب: $adminNote", amount)
                } else {
                    _requestActionError.value = present(
                        error,
                        "reject_withdrawal",
                        verifiedOutcome(error, refreshed),
                    )
                }
            }
        _isRequestActionInProgress.value = false
    }

    fun completeRequest(id: String, adminNote: String) = scope.launch {
        if (!commandAdapter.ensureFinancialMutationsEnabled { failure ->
                _requestActionError.value = present(failure, "complete_withdrawal_guard")
            }) return@launch
        if (!permissionProvider.canNow { it.commissionManage }) {
            _requestActionError.value = permissionError("complete_withdrawal")
            return@launch
        }
        if (_isRequestActionInProgress.value) return@launch
        val previous = _withdrawalRequests.value.find { it.id == id }
        _isRequestActionInProgress.value = true
        withdrawalRepo.completeRequest(id, adminNote)
            .onSuccess {
                _requestActionError.value = null
                val refreshedRequests = refreshWithdrawalStateFromServer()
                refreshCommissionPayments()
                val req = refreshedRequests?.find { r -> r.id == id } ?: previous ?: return@onSuccess
                commandAdapter.logWithdrawalAudit(AuditAction.UPDATE, id, "إتمام طلب سحب", req.amount.toRemoteDouble())
                launch notification@{
                    val profile = authRepository.getMyProfile() ?: return@notification
                    val userId = withdrawalRepo.getMarketerUserId(req.clientId) ?: return@notification
                    withdrawalRepo.insertNotification(
                        userId = userId,
                        clientId = req.clientId,
                        orgId = profile.organizationId,
                        type = "WITHDRAWAL_COMPLETED",
                        title = "تم إتمام السحب ✅",
                        body = "تم تحويل ${String.format("%.2f", req.amount)} إلى حسابك",
                    )
                }
            }
            .onFailure { error ->
                val refreshed = refreshWithdrawalStateFromServer()
                refreshCommissionPayments()
                if (hasStatus(refreshed, id, WithdrawalStatus.COMPLETED)) {
                    _requestActionError.value = null
                    val req = refreshed?.find { it.id == id } ?: previous
                    if (req != null) {
                        commandAdapter.logWithdrawalAudit(AuditAction.UPDATE, id, "إتمام طلب سحب", req.amount.toRemoteDouble())
                    }
                } else {
                    _requestActionError.value = present(
                        error,
                        "complete_withdrawal",
                        verifiedOutcome(error, refreshed),
                    )
                }
            }
        _isRequestActionInProgress.value = false
    }

    fun generateJoinCode(client: MarketingClient) = scope.launch {
        if (!permissionProvider.canNow { it.commissionManage }) {
            _codeError.value = permissionError("issue_join_code")
            return@launch
        }
        if (_isGeneratingCode.value) return@launch
        _isGeneratingCode.value = true
        _codeError.value = null
        try {
            authRepository.issueAutodriveJoinCode(client.id, client.accountType)
                .onSuccess {
                    _generatedCode.value = it to client.name
                    _codeError.value = null
                }.onFailure { error ->
                    _codeError.value = present(error, "issue_join_code")
                    android.util.Log.e("CommissionVM", "Commission code issue failed: ${error::class.java.simpleName}")
                }
        } finally {
            _isGeneratingCode.value = false
        }
    }

    fun clearCodeError() { _codeError.value = null }
    fun clearGeneratedCode() { _generatedCode.value = null }

    override fun sharePdf(file: java.io.File) {
        documentSharePort.sharePdf(file)
    }

    override fun buildMarketerReport(clientId: String, clientName: String): MarketerCommissionReport =
        com.verto.app.pdf.buildMarketerCommissionReport(
            rows = _serverEligibility.value ?: emptyList(),
            clientId = clientId,
            clientName = clientName,
            from = filterFrom.value,
            to = filterTo.value,
        )

    private fun refreshCommissionPayments() = scope.launch {
        runCatching {
            val orgId = authRepository.getMyProfile()?.organizationId ?: return@launch
            syncManager.pullCommissions(orgId)
        }
    }

    fun deleteCommissionPayment(id: String) = scope.launch {
        _requestActionError.value = present(
            AppFailure.BusinessRule(code = "COMMISSION_PAYMENT_DELETE_FORBIDDEN"),
            "delete_commission_payment",
        )
    }

    private fun present(
        failure: AppFailure,
        operation: String,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    ): UserErrorPresentation = UserErrorFactory.from(
        failure = failure,
        context = ErrorPresentationContext.TRANSIENT_ACTION,
        operation = operation,
        outcome = outcome,
    )

    private fun present(
        error: Throwable,
        operation: String,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    ): UserErrorPresentation = UserErrorFactory.from(
        throwable = error,
        context = ErrorPresentationContext.TRANSIENT_ACTION,
        operation = operation,
        outcome = outcome,
    )

    private fun permissionError(operation: String): UserErrorPresentation = present(
        AppFailure.PermissionDenied(target = "commission"),
        operation,
    )

    /**
     * A completed server read is authoritative for withdrawal state. If verification itself cannot
     * complete, only failures that may have crossed the server boundary are marked unknown.
     */
    private fun verifiedOutcome(
        error: Throwable,
        refreshed: List<WithdrawalRequestDto>?,
    ): OperationOutcome = if (refreshed != null) {
        OperationOutcome.NOT_APPLIED
    } else {
        uncertainFinancialOutcome(error)
    }

    private fun uncertainFinancialOutcome(error: Throwable): OperationOutcome = when (ErrorClassifier.classify(error)) {
        is AppFailure.Timeout,
        is AppFailure.ConnectionFailed,
        is AppFailure.Server,
        is AppFailure.Unknown,
        -> OperationOutcome.OUTCOME_UNKNOWN
        else -> OperationOutcome.NOT_APPLIED
    }

    private fun hasStatus(
        rows: List<WithdrawalRequestDto>?,
        id: String,
        expected: WithdrawalStatus,
    ): Boolean = rows
        ?.firstOrNull { it.id == id }
        ?.let { WithdrawalStatus.fromWire(it.status) == expected }
        ?: false

    }
}
