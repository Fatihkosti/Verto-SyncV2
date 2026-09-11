package com.verto.app.feature.commission.application

import com.verto.app.core.error.UserErrorPresentation
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Pure commission models exposed to presentation. */
data class CommissionInvoiceModel(
    val id: String,
    val invoiceNumber: Int,
    val clientId: String,
    val totalAmount: Double,
    val commission: Double,
    val createdAt: Long
)

data class InvoiceWithClientName(
    val invoice: CommissionInvoiceModel,
    val clientName: String,
    val clientTypeLabel: String
)

data class ClientCommissionBalance(
    val clientId: String,
    val clientName: String,
    val totalCommission: Double,
    val withdrawableCommission: Double,
    val withdrawableInvoices: List<CommissionInvoiceModel>
)

enum class CommissionCard { TOTAL, WITHDRAWABLE, PENDING, PAID, EARNING }

data class EarningCommissionItem(
    val invoiceId: String,
    val invoiceNumber: Int?,
    val clientName: String,
    val clientTypeLabel: String,
    val invoiceTotal: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val commissionAmount: Double,
    val creditedAmount: Double,
    val earningAmount: Double,
    val status: String
)

data class MarketingClient(
    val id: String,
    val name: String,
    val typeLabel: String,
    val accountType: String = "MARKETER",
    val linkStatus: MarketerLinkStatus = MarketerLinkStatus.NOT_LINKED
)

enum class MarketerLinkStatus(val label: String) {
    REGISTERED("مسجّل"),
    PENDING_CODE("كود معلّق"),
    NOT_LINKED("غير مربوط")
}

enum class CommissionWithdrawalStatus(val wireValue: String, val label: String) {
    PENDING("PENDING", "معلق"),
    APPROVED("APPROVED", "موافق عليه"),
    REJECTED("REJECTED", "مرفوض"),
    COMPLETED("COMPLETED", "مكتمل"),
    UNKNOWN("UNKNOWN", "");

    companion object {
        fun fromWire(value: String): CommissionWithdrawalStatus =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

data class CommissionWithdrawalRequest(
    val id: String,
    val clientId: String,
    val amount: Double,
    val status: String,
    val bankName: String,
    val bankAccount: String,
    val transactionRef: String?,
    val note: String?,
    val adminNote: String?,
    val requestedAt: String
)

enum class MarketerBalanceState {
    AVAILABLE,
    UNAVAILABLE,
}

data class WithdrawalRequestUiModel(
    val request: CommissionWithdrawalRequest,
    val status: CommissionWithdrawalStatus,
    val clientName: String,
    val marketerBalance: Double?,
    val balanceState: MarketerBalanceState,
    val pendingTotalForClient: Double,
    val isAmountOverBalance: Boolean,
    val isPendingTotalOverBalance: Boolean
)

data class CommissionActivityItem(
    val id: String,
    val clientId: String,
    val clientName: String,
    val invoiceIds: String,
    val totalAmount: Double,
    val bankName: String,
    val transactionRef: String,
    val paidAt: Long
)

data class CommissionPermissions(
    val commissionManage: Boolean,
    val marketingDashboards: Boolean
)

sealed interface CommissionAttentionItem {
    val key: String

    data class WithdrawalRequest(
        val requestId: String,
        val clientId: String,
        val clientName: String,
        val amount: Double,
        val status: CommissionWithdrawalStatus,
        val requestedAt: String,
    ) : CommissionAttentionItem {
        override val key: String = "withdrawal:$requestId"
    }

    data class ReadyPayout(
        val clientId: String,
        val clientName: String,
        val amount: Double,
        val invoicesCount: Int,
    ) : CommissionAttentionItem {
        override val key: String = "payout:$clientId"
    }
}

data class CommissionAttentionSummary(
    val openWithdrawalRequestsCount: Int = 0,
    val openWithdrawalRequestsAmount: Double = 0.0,
    val items: List<CommissionAttentionItem> = emptyList(),
)

data class CommissionUiState(
    val totalCommission: Double = 0.0,
    val withdrawableCommission: Double = 0.0,
    val pendingCommission: Double = 0.0,
    val paidCommission: Double = 0.0,
    val earningCommission: Double = 0.0,
    val filteredInvoices: List<InvoiceWithClientName> = emptyList(),
    val earningItems: List<EarningCommissionItem> = emptyList(),
    val clientBalances: List<ClientCommissionBalance> = emptyList(),
    val activityLog: List<CommissionActivityItem> = emptyList(),
    val selectedCard: CommissionCard = CommissionCard.TOTAL,
    val filterFrom: Long? = null,
    val filterTo: Long? = null,
    val paymentSuccess: Boolean = false,
    val marketingClients: List<MarketingClient> = emptyList(),
    val generatedCode: String? = null,
    val generatedCodeClientName: String = "",
    val isGeneratingCode: Boolean = false,
    val codeError: UserErrorPresentation? = null,
    val withdrawalRequests: List<CommissionWithdrawalRequest> = emptyList(),
    val withdrawalRequestItems: List<WithdrawalRequestUiModel> = emptyList(),
    val pendingRequestsCount: Int = 0,
    val requestActionError: UserErrorPresentation? = null,
    val isPayingOut: Boolean = false,
    val isRequestActionInProgress: Boolean = false,
    val isApproximate: Boolean = true,
    val attention: CommissionAttentionSummary = CommissionAttentionSummary(),
)

data class MarketerCommissionRow(
    val invoiceNumber: Int,
    val dateLabel: String,
    val invoiceTotal: Double,
    val commission: Double,
    val statusLabel: String
)

data class MarketerCommissionReport(
    val marketerName: String,
    val periodLabel: String,
    val rows: List<MarketerCommissionRow>,
    val totalCommission: Double,
    val withdrawableTotal: Double,
    val paidTotal: Double,
    val pendingTotal: Double
)

sealed interface CommissionAction {
    data class SetFilter(val from: Long, val to: Long) : CommissionAction
    object ClearFilter : CommissionAction
    object ShowAllPeriods : CommissionAction
    data class SelectCard(val card: CommissionCard) : CommissionAction
    object ClearPaymentSuccess : CommissionAction
    object ClearRequestActionError : CommissionAction
    object LoadServerData : CommissionAction
    object LoadWithdrawalRequests : CommissionAction
    data class WithdrawAll(
        val clientId: String,
        val clientName: String,
        val bankName: String,
        val transactionRef: String
    ) : CommissionAction
    data class WithdrawSingle(
        val invoice: CommissionInvoiceModel,
        val clientName: String,
        val bankName: String,
        val transactionRef: String
    ) : CommissionAction
    data class WithdrawFreeAmount(
        val clientId: String,
        val clientName: String,
        val amount: Double,
        val bankName: String,
        val transactionRef: String
    ) : CommissionAction
    data class ApproveRequest(val id: String, val transactionRef: String) : CommissionAction
    data class RejectRequest(val id: String, val adminNote: String) : CommissionAction
    data class CompleteRequest(val id: String, val adminNote: String) : CommissionAction
    data class GenerateJoinCode(val client: MarketingClient) : CommissionAction
    object ClearCodeError : CommissionAction
    object ClearGeneratedCode : CommissionAction
    data class DeleteCommissionPayment(val id: String) : CommissionAction
}

interface CommissionController {
    val role: StateFlow<String?>
    val permissions: StateFlow<CommissionPermissions?>
    val uiState: StateFlow<CommissionUiState>
    fun dispatch(action: CommissionAction)
    fun sharePdf(file: File)
    fun buildMarketerReport(clientId: String, clientName: String): MarketerCommissionReport
}

interface CommissionControllerFactory {
    fun create(scope: CoroutineScope): CommissionController
}
