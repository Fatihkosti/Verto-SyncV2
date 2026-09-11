package com.verto.app.data.repository

import com.verto.app.core.error.RemoteFailureBoundary
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.CommissionLedgerDto
import com.verto.app.data.remote.dto.ConversationDto
import com.verto.app.data.remote.dto.InternalMessageDto
import com.verto.app.data.remote.dto.MarketerBalanceDto
import com.verto.app.data.remote.dto.MarketerStatsDto
import com.verto.app.data.remote.dto.RegisteredMarketerDto
import com.verto.app.data.remote.dto.WithdrawalRequestDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CancellationException

class FinancialCommandException(
    val command: String,
    cause: Throwable
) : IllegalStateException(
    "Financial command failed: $command",
    RemoteFailureBoundary.wrap(cause),
) {
    /** Compatibility for existing call sites: legacy message is intentionally discarded. */
    constructor(
        command: String,
        @Suppress("UNUSED_PARAMETER") message: String,
        cause: Throwable,
    ) : this(command, cause)
}

internal fun nowIso(): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
).also { it.timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

internal fun financialCommandFailure(command: String, cause: Throwable): Throwable {
    if (cause is CancellationException) throw cause
    return FinancialCommandException(command = command, cause = cause)
}

internal suspend fun <T> runFinancialCommand(
    command: String,
    block: suspend () -> T
): Result<T> = runCatching { block() }.fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure(financialCommandFailure(command, it)) }
)

class WithdrawalRepository(authRepository: AuthRepository) {
    private val withdrawalCommandRemoteSource = WithdrawalCommandRemoteSource(authRepository)
    private val commissionRemoteSource = CommissionRemoteSource(authRepository)
    private val internalMessagingRemoteSource = InternalMessagingRemoteSource(authRepository)

    suspend fun currentOrgId(): String? = commissionRemoteSource.currentOrgId()
    suspend fun getCommissionEligibility(): Result<List<CommissionEligibilityDto>> = commissionRemoteSource.getCommissionEligibility()
    suspend fun getMarketerBalances(): Result<List<MarketerBalanceDto>> = commissionRemoteSource.getMarketerBalances()
    suspend fun getCommissionLedger(): Result<List<CommissionLedgerDto>> = commissionRemoteSource.getCommissionLedger()
    suspend fun getMarketerStats(): Result<List<MarketerStatsDto>> = commissionRemoteSource.getMarketerStats()

    suspend fun sendInactivityReminder(clientId: String, orgId: String, marketerName: String): Result<Unit> =
        commissionRemoteSource.sendInactivityReminder(clientId, orgId, marketerName)

    suspend fun sendAdminReminder(clientId: String, orgId: String, message: String, navRoute: String): Result<Unit> =
        commissionRemoteSource.sendAdminReminder(clientId, orgId, message, navRoute)

    suspend fun getPaidOutCommissionInvoiceIds(): Result<Set<String>> = commissionRemoteSource.getPaidOutCommissionInvoiceIds()
    suspend fun getWithdrawalRequests(): Result<List<WithdrawalRequestDto>> = withdrawalCommandRemoteSource.getWithdrawalRequests()
    suspend fun getPendingWithdrawalRequestsCount(): Result<Int> = withdrawalCommandRemoteSource.getPendingWithdrawalRequestsCount()
    suspend fun approveRequest(id: String, transactionRef: String): Result<Unit> = withdrawalCommandRemoteSource.approveRequest(id, transactionRef)
    suspend fun rejectRequest(id: String, adminNote: String): Result<Unit> = withdrawalCommandRemoteSource.rejectRequest(id, adminNote)
    suspend fun completeRequest(id: String, adminNote: String): Result<Unit> = withdrawalCommandRemoteSource.completeRequest(id, adminNote)

    suspend fun creditMarketerBalance(clientId: String, orgId: String, amount: Double, referenceId: String, note: String): Result<Unit> =
        commissionRemoteSource.creditMarketerBalance(clientId, orgId, amount, referenceId, note)

    suspend fun payOutFreeAmount(clientId: String, amount: Double, bankName: String, txRef: String, clientRequestId: String): Result<String> =
        commissionRemoteSource.payOutFreeAmount(clientId, amount, bankName, txRef, clientRequestId)

    suspend fun payOutCommission(invoiceIds: List<String>, bankName: String, txRef: String, clientRequestId: String): Result<String> =
        commissionRemoteSource.payOutCommission(invoiceIds, bankName, txRef, clientRequestId)

    suspend fun getMarketerUserId(clientId: String): String? = commissionRemoteSource.getMarketerUserId(clientId)

    suspend fun insertNotification(userId: String, clientId: String, orgId: String, type: String, title: String, body: String): Result<Unit> =
        commissionRemoteSource.insertNotification(userId, clientId, orgId, type, title, body)

    suspend fun getConversations(): Result<List<ConversationDto>> = internalMessagingRemoteSource.getConversations()
    suspend fun getMessages(clientId: String): Result<List<InternalMessageDto>> = internalMessagingRemoteSource.getMessages(clientId)
    suspend fun sendAdminMessage(clientId: String, body: String): Result<InternalMessageDto> = internalMessagingRemoteSource.sendAdminMessage(clientId, body)
    suspend fun markMessagesRead(clientId: String): Result<Unit> = internalMessagingRemoteSource.markMessagesRead(clientId)
    suspend fun markConversationRead(conversationId: String): Result<Unit> = internalMessagingRemoteSource.markConversationRead(conversationId)
    suspend fun getMessagesByConversation(conversationId: String): Result<List<InternalMessageDto>> = internalMessagingRemoteSource.getMessagesByConversation(conversationId)

    suspend fun sendAdminMediaMessage(
        conversationId: String?,
        clientId: String,
        type: String,
        body: String,
        mediaBytes: ByteArray?,
        mimeType: String?,
        durationMs: Long?
    ): Result<InternalMessageDto> = internalMessagingRemoteSource.sendAdminMediaMessage(
        AdminMediaMessageCommand(conversationId, clientId, type, body, mediaBytes, mimeType, durationMs)
    )

    suspend fun deleteMessage(messageId: String): Result<Unit> = internalMessagingRemoteSource.deleteMessage(messageId)
    suspend fun deleteConversation(conversationId: String): Result<Unit> = internalMessagingRemoteSource.deleteConversation(conversationId)
    suspend fun openConversation(clientId: String, subject: String = ""): Result<ConversationDto> = internalMessagingRemoteSource.openConversation(clientId, subject)
    suspend fun getRegisteredMarketers(): Result<List<RegisteredMarketerDto>> = commissionRemoteSource.getRegisteredMarketers()
    suspend fun getTotalUnreadCount(): Result<Int> = internalMessagingRemoteSource.getTotalUnreadCount()
}
