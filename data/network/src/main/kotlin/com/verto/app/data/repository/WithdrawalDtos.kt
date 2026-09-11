package com.verto.app.data.repository

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal

// ── DTOs داخلية للعمليات ──────────────────────────────────────────────────────

@Serializable
internal data class WithdrawalRejectUpdate(
    val status: String,
    @SerialName("admin_note") val adminNote: String,
    @SerialName("processed_at") val processedAt: String,
    @SerialName("processed_by") val processedBy: String
)

@Serializable
internal data class ApproveWithdrawalParams(
    @SerialName("p_withdrawal_id") val withdrawalId: String,
    @SerialName("p_transaction_ref") val transactionRef: String,
    @SerialName("p_client_request_id") val clientRequestId: String? = null
)

@Serializable
internal data class CompleteWithdrawalParams(
    @SerialName("p_withdrawal_id") val withdrawalId: String,
    @SerialName("p_admin_note") val adminNote: String
)

@Serializable
internal data class PayOutFreeAmountParams(
    @SerialName("p_client_id")       val clientId: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("p_amount")          val amount: BigDecimal,
    @SerialName("p_bank_name")       val bankName: String,
    @SerialName("p_transaction_ref") val transactionRef: String,
    @SerialName("p_client_request_id") val clientRequestId: String
)

@Serializable
internal data class PayOutCommissionParams(
    @SerialName("p_invoice_ids")        val invoiceIds: List<String>,
    @SerialName("p_bank_name")          val bankName: String,
    @SerialName("p_transaction_ref")    val transactionRef: String,
    @SerialName("p_client_request_id")  val clientRequestId: String? = null
)

@Serializable
internal data class CreditBalanceParams(
    @SerialName("p_client_id") val clientId: String,
    @SerialName("p_org_id") val orgId: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("p_amount") val amount: BigDecimal,
    @SerialName("p_reference_id") val referenceId: String,
    @SerialName("p_note") val note: String
)

@Serializable
internal data class NotificationInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("org_id") val orgId: String,
    val type: String,
    val title: String,
    val body: String,
    val data: JsonObject = buildJsonObject {}
)

@Serializable
internal data class AdminMessageInsert(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_type") val senderType: String,
    val body: String
)

@Serializable
internal data class AdminMediaMessageInsert(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("conversation_id") val conversationId: String?,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_type") val senderType: String,
    val type: String,
    val body: String,
    @SerialName("media_url") val mediaUrl: String?,
    @SerialName("media_mime") val mediaMime: String?,
    @SerialName("media_duration_ms") val mediaDurationMs: Long?
)

@Serializable
internal data class MarkReadUpdate(
    @SerialName("is_read") val isRead: Boolean
)

@Serializable
internal data class ConversationUnreadUpdate(
    @SerialName("admin_unread") val adminUnread: Int
)

@Serializable
internal data class CreateNewConversationParams(
    @SerialName("p_client_id") val clientId: String,
    @SerialName("p_subject") val subject: String = ""
)

@Serializable
internal data class ConversationInsert(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("client_id") val clientId: String,
    val subject: String
)
